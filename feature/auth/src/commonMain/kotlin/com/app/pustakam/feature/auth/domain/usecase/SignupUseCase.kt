package com.app.pustakam.feature.auth.domain.usecase

import com.app.pustakam.core.model.models.BaseResponse
import com.app.pustakam.core.model.models.request.RegisterReq
import com.app.pustakam.core.model.models.response.User
import kotlinx.coroutines.flow.Flow
import com.app.pustakam.core.common.util.Error
import com.app.pustakam.core.common.util.Result

class SignUseCase : AuthBaseUseCase() {
    suspend operator fun invoke(user: RegisterReq): Flow<Result<BaseResponse<User>, Error>> = getBaseApiCall {
        authRepository.registerUser(user = user)
    }
}

class DeleteUserUseCase : AuthBaseUseCase() {
    suspend operator fun invoke() = authRepository.deleteUser()
}

// 🗑️ 31-Aug-2026 — SUPERSEDED by domain/profile/UpdateProfileUseCase. It posts a whole User
//   at a .strict() schema and would 422; zero call sites, so nothing ever hit that.
class UpdateUserUseCase : AuthBaseUseCase() {
    suspend operator fun invoke(user: User) = authRepository.updateUser(user)
}

// 🗑️ 31-Aug-2026 — SUPERSEDED by domain/profile/GetMyProfileUseCase. Zero call sites, and the
//   parameter is typed User rather than String and never read, so it can only ever fetch the
//   CURRENT user. Kept rather than deleted per the project's delete-nothing rule; do not
//   build on it.
class ReadUserUseCase : AuthBaseUseCase() {
    suspend operator fun invoke(userId: User) = authRepository.getUser("")
}
