package com.app.pustakam.feature.auth.domain.repository

import com.app.pustakam.core.common.extensions.normalizedPhone
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.common.util.onSuccess
import com.app.pustakam.core.data.base.BaseRepository
import com.app.pustakam.core.database.localdb.preferences.UserPreference
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.request.Login
import com.app.pustakam.core.model.models.request.RegisterReq
import com.app.pustakam.core.model.models.profile.PublicUser
import com.app.pustakam.core.model.models.profile.SetUsernameReq
import com.app.pustakam.core.model.models.profile.UpdateProfileReq
import com.app.pustakam.core.model.models.profile.UsernameAvailability
import com.app.pustakam.core.model.models.response.User
import com.app.pustakam.core.model.validation.UsernameRules
import com.app.pustakam.core.network.MediaUpload
import kotlinx.coroutines.flow.StateFlow

internal class AuthRepository : BaseRepository(), IAuthRepository {

    override val authState: StateFlow<UserPreference> = session.state

    override suspend fun loginUser(login: Login): Result<BaseResponse<User>, Error> =
        apiClient.login(login.canonical()).onSuccess { startSession(it.data) }

    // 🔐 20-Aug-2026 sync: register used to store NOTHING, so a fresh signup had no session to sync with
    override suspend fun registerUser(user: RegisterReq): Result<BaseResponse<User>, Error> =
        apiClient.register(user.canonical()).onSuccess { startSession(it.data) }

    /** 📞 28-Aug-2026 — ONE canonical shape leaves the app, whichever screen collected it. Sign-up
     *  stored the number the way it was typed there and sign-in matched the way it was typed here,
     *  so a space or a "+91" on one of the two screens was enough to make a correct phone number
     *  read as wrong credentials. Doing it here covers Android and iOS at once. */
    private fun Login.canonical(): Login = copy(
        email = email?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
        phone = phone.normalizedPhone().takeIf { it.isNotBlank() },
    )

    private fun RegisterReq.canonical(): RegisterReq = copy(
        email = email?.trim()?.lowercase(),
        phone = phone.normalizedPhone(),
    )

    /** 🔐 the single place a session begins: /login, /register and /auth/refresh all answer
     *  with the user spread together with both tokens. The refresh token is what lets background
     *  sync outlive the 15-minute access token. */
    private suspend fun startSession(user: User?) {
        val id = user?._id ?: return
        user.accessToken?.takeIf { it.isNotBlank() }?.let { userPrefs.setToken(it) }
        user.refreshToken?.takeIf { it.isNotBlank() }?.let { userPrefs.setRefreshToken(it) }
        userPrefs.setUserId(id)
        userPrefs.setAuth(true)
    }

    override suspend fun updateUser(user: User): Result<BaseResponse<User>, Error> =
        apiClient.updateUser(user)

    // pass empty string to get current user
    override suspend fun getUser(userId: String): Result<BaseResponse<User>, Error> =
        apiClient.getUser(userId.ifEmpty { session.userId })

    override suspend fun deleteUser(): Result<BaseResponse<User>, Error> =
        apiClient.deleteUser(session.userId)

    override suspend fun uploadAvatar(file: MediaUpload): Result<BaseResponse<User>, Error> =
        apiClient.uploadAvatar(file)

    override suspend fun updateProfile(request: UpdateProfileReq): Result<BaseResponse<User>, Error> =
        apiClient.updateProfile(session.userId, request)

    /** 🆔 canonicalised here so both platforms send the same thing — same place as Login.canonical(). */
    override suspend fun setUsername(username: String): Result<BaseResponse<User>, Error> =
        apiClient.setUsername(session.userId, SetUsernameReq(UsernameRules.canonical(username)))

    override suspend fun checkUsername(username: String): Result<BaseResponse<UsernameAvailability>, Error> =
        apiClient.checkUsername(UsernameRules.canonical(username))

    override suspend fun getPublicProfile(username: String): Result<BaseResponse<PublicUser>, Error> =
        apiClient.getPublicProfile(UsernameRules.canonical(username))

    override suspend fun searchPeople(query: String, limit: Int): Result<BaseResponse<List<PublicUser>>, Error> =
        apiClient.searchPeople(UsernameRules.canonical(query), limit)

    override suspend fun userLogout() {
        userPrefs.clear()
    }
}
