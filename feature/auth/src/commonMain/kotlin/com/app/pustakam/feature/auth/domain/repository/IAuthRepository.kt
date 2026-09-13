package com.app.pustakam.feature.auth.domain.repository

import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result
import com.app.pustakam.core.database.localdb.preferences.UserPreference
import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.request.Login
import com.app.pustakam.core.model.models.request.RegisterReq
import com.app.pustakam.core.model.models.profile.PublicUser
import com.app.pustakam.core.model.models.profile.SetUsernameReq
import com.app.pustakam.core.model.models.profile.UpdateProfileReq
import com.app.pustakam.core.model.models.profile.UsernameAvailability
import com.app.pustakam.core.model.models.response.User
import com.app.pustakam.core.network.MediaUpload
import kotlinx.coroutines.flow.StateFlow

interface IAuthRepository {
    suspend fun loginUser(login: Login): Result<BaseResponse<User>, Error>
    suspend fun registerUser(user: RegisterReq): Result<BaseResponse<User>, Error>
    suspend fun updateUser(user: User): Result<BaseResponse<User>, Error>
    suspend fun getUser(userId: String): Result<BaseResponse<User>, Error>
    suspend fun deleteUser(): Result<BaseResponse<User>, Error>
    // 🖼️ 31-Aug-2026 — was a stub that posted an empty form body. uploadAvatar replaces it:
    //   one round trip that uploads, sets avatarAssetId server-side and returns the new user.
    suspend fun uploadAvatar(file: MediaUpload): Result<BaseResponse<User>, Error>

    // 👤 31-Aug-2026 profile
    suspend fun updateProfile(request: UpdateProfileReq): Result<BaseResponse<User>, Error>
    suspend fun setUsername(username: String): Result<BaseResponse<User>, Error>
    suspend fun checkUsername(username: String): Result<BaseResponse<UsernameAvailability>, Error>
    suspend fun getPublicProfile(username: String): Result<BaseResponse<PublicUser>, Error>
    suspend fun searchPeople(query: String, limit: Int = PEOPLE_PAGE_SIZE): Result<BaseResponse<List<PublicUser>>, Error>
    suspend fun userLogout()

    val authState: StateFlow<UserPreference>
}

const val PEOPLE_PAGE_SIZE = 20
