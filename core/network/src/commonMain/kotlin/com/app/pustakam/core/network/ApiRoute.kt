package com.app.pustakam.core.network

 enum class ApiRoute {

    LOGIN {
        override fun getName(): String ="$baseUrl/login"
     },
     REGISTER {
         override fun getName(): String ="$baseUrl/register"
     },
     // 🔐 20-Aug-2026 sync: rotating refresh — access tokens live 15 minutes, background sync outlives that
     AUTH_REFRESH {
         override fun getName(): String = "$baseUrl/auth/refresh"
     },
     NOTES {
         override fun getName(): String = "$baseUrl/notes"
     },
     // 🔄 20-Aug-2026 sync: /sync/{userId}/push and /sync/{userId}/pull
     SYNC {
         override fun getName(): String = "$baseUrl/sync"
     },
     // 🖼️ 20-Aug-2026 sync phase 2: multipart upload, field name "files"
     IMAGES {
         override fun getName(): String = "$baseUrl/images"
     },
     // 🖼️ 20-Aug-2026 sync phase 2: /media/{userId}/{assetId}
     MEDIA {
         override fun getName(): String = "$baseUrl/media"
     },
     PROFILE {
         override fun getName(): String = "$baseUrl/profile"
             },
     USERS{
         override fun getName(): String = "$baseUrl/users"
     },
     // 💬 31-Aug-2026 chat: /chat/conversations, /chat/conversations/{id}/messages, /chat/peers
     CHAT {
         override fun getName(): String = "$baseUrl/chat"
     },
     // 🆔 31-Aug-2026 profile: /u/check, /u/search, /u/{username}. Short on purpose — it is
     //   also the deep-link shape, pustakam://u/<username>.
     PROFILE_PUBLIC {
         override fun getName(): String = "$baseUrl/u"
     };

     val baseUrl = getUrl()
     abstract fun getName(): String
}
const val headerAuth = "Authorization"
