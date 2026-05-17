package com.example.smartstock.data.auth

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    @Serializable
    private data class ProfileRow(
        val id: String,
        val email: String? = null,
        @SerialName("display_name") val displayName: String? = null,
        val role: String = ROLE_STAFF,
        @SerialName("is_active") val isActive: Boolean = true,
        @SerialName("team_id") val teamId: String? = null
    )

    @Serializable
    private data class TeamRow(
        val id: String,
        val name: String
    )

    @Serializable
    private data class CreateStaffRequest(
        val name: String,
        val email: String,
        val password: String,
        val role: String = ROLE_STAFF
    )

    @Serializable
    private data class CreateStaffResponse(
        @SerialName("user_id") val userId: String? = null,
        val email: String? = null,
        val error: String? = null
    )

    suspend fun signIn(email: String, password: String): Result<AuthUser> = runCatching {
        supabase.auth.signInWith(Email) {
            this.email = email.trim().lowercase()
            this.password = password
        }
        ensureTeam()
        currentUser() ?: error("Sign-in succeeded but no session was created.")
    }

    suspend fun signUp(name: String, email: String, password: String): Result<AuthUser> = runCatching {
        // Pass display_name through user metadata so the handle_new_user
        // trigger writes it directly into profiles on first insert. Team
        // creation is deferred to bootstrap_team_for_user() called below
        // — keeping the auth trigger minimal makes sign-up bulletproof.
        supabase.auth.signUpWith(Email) {
            this.email = email.trim().lowercase()
            this.password = password
            data = buildJsonObject {
                put("display_name", name.trim())
            }
        }
        ensureTeam()
        currentUser() ?: error(
            "Account created. Please check your email to confirm before signing in."
        )
    }

    /**
     * Idempotent. Calls the bootstrap RPC so every session lands with a
     * valid team_id and an admin role for self-signups. Failures are
     * logged but not raised — a session without a team still lets the
     * user reach the Profile screen, where the next sync attempt will
     * retry the bootstrap.
     */
    private suspend fun ensureTeam() {
        if (supabase.auth.currentSessionOrNull() == null) return
        // handle_new_user() now provisions a team in the sign-up trigger,
        // so this RPC is just an idempotent safety net (it returns the
        // existing team_id immediately when one is already set). Retry
        // once on a transient failure rather than silently giving up —
        // a session that stays team-less would be invisible to its own
        // data and could leak into the legacy bucket.
        repeat(2) { attempt ->
            val result = runCatching { supabase.postgrest.rpc(RPC_BOOTSTRAP_TEAM) }
            if (result.isSuccess) return
            Log.w(TAG, "bootstrap_team_for_user attempt ${attempt + 1} failed",
                result.exceptionOrNull())
        }
    }

    suspend fun signOut() {
        runCatching { supabase.auth.signOut() }
    }

    suspend fun resetPasswordForEmail(email: String): Result<Unit> = runCatching {
        supabase.auth.resetPasswordForEmail(email.trim().lowercase())
    }

    suspend fun currentUser(): AuthUser? {
        val session = supabase.auth.currentSessionOrNull() ?: return null
        val user = session.user ?: return null
        val profile = runCatching {
            supabase.postgrest.from(TABLE_PROFILES)
                .select { filter { eq("id", user.id) } }
                .decodeSingleOrNull<ProfileRow>()
        }.getOrNull()
        val teamName = profile?.teamId?.let { teamId ->
            runCatching {
                supabase.postgrest.from(TABLE_TEAMS)
                    .select { filter { eq("id", teamId) } }
                    .decodeSingleOrNull<TeamRow>()?.name
            }.getOrNull()
        }
        val metadataName = (user.userMetadata?.get("display_name") as? JsonPrimitive)?.content
        return AuthUser(
            id = user.id,
            email = profile?.email ?: user.email.orEmpty(),
            name = profile?.displayName
                ?: metadataName
                ?: user.email.orEmpty().substringBefore('@').ifBlank { "User" },
            role = profile?.role ?: ROLE_STAFF,
            isActive = profile?.isActive ?: true,
            teamId = profile?.teamId,
            teamName = teamName
        )
    }

    /**
     * Lists everyone in the caller's team. RLS scopes this to same-team
     * profiles, so a Staff member sees the same list as their Admin.
     */
    suspend fun listTeamMembers(): List<TeamMember> {
        return runCatching {
            supabase.postgrest.from(TABLE_PROFILES)
                .select {
                    order(column = "role", order = Order.ASCENDING)
                    order(column = "display_name", order = Order.ASCENDING)
                }
                .decodeList<ProfileRow>()
                .map { it.toTeamMember() }
        }.getOrElse {
            Log.w(TAG, "listTeamMembers failed", it)
            emptyList()
        }
    }

    /**
     * Admin-only. Calls the create-staff Edge Function which uses the
     * service-role key server-side to spin up a new auth user under
     * this admin's team_id with the supplied display_name + role.
     */
    suspend fun createStaff(
        name: String,
        email: String,
        password: String,
        role: String = ROLE_STAFF
    ): Result<String> = runCatching {
        val response = supabase.functions.invoke(
            function = FUNCTION_CREATE_STAFF,
            body = CreateStaffRequest(
                name = name.trim(),
                email = email.trim().lowercase(),
                password = password,
                role = if (role == ROLE_ADMIN) ROLE_ADMIN else ROLE_STAFF
            )
        )
        val parsed: CreateStaffResponse = response.body()
        if (parsed.error != null) error(parsed.error)
        parsed.userId ?: error("create-staff returned no user_id")
    }

    /**
     * Promotes/demotes a teammate. RLS allows this only for an active
     * admin within the same team.
     */
    suspend fun setMemberRole(userId: String, role: String): Result<Unit> = runCatching {
        val target = if (role == ROLE_ADMIN) ROLE_ADMIN else ROLE_STAFF
        supabase.postgrest.from(TABLE_PROFILES).update(
            update = { set("role", target) }
        ) {
            filter { eq("id", userId) }
        }
    }

    /**
     * Activates/deactivates a teammate. Inactive users can still sign in
     * but the app blocks them at login (see InventoryViewModel).
     */
    suspend fun setMemberActive(userId: String, isActive: Boolean): Result<Unit> = runCatching {
        supabase.postgrest.from(TABLE_PROFILES).update(
            update = { set("is_active", isActive) }
        ) {
            filter { eq("id", userId) }
        }
    }

    private fun ProfileRow.toTeamMember(): TeamMember = TeamMember(
        id = id,
        email = email.orEmpty(),
        displayName = displayName ?: email.orEmpty().substringBefore('@'),
        role = role,
        isActive = isActive
    )

    companion object {
        private const val TABLE_PROFILES = "profiles"
        private const val TABLE_TEAMS = "teams"
        private const val RPC_BOOTSTRAP_TEAM = "bootstrap_team_for_user"
        private const val FUNCTION_CREATE_STAFF = "create-staff"
        private const val TAG = "SupabaseAuth"
        const val ROLE_ADMIN = "Admin"
        const val ROLE_STAFF = "Staff"
    }
}
