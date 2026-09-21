package com.app.pustakam.core.common.util

enum class NetworkError : Error {

    CONNECTION_FAILED {
        override fun getError(): String {
            return "Could not reach the server. Please try again."
        }
    },
    NOT_FOUND {
        override fun getError(): String {
            return "No Record found"
        }
              },
    REQUEST_TIMEOUT {
        override fun getError(): String {
            return "Request Timeout, Please try again later."
        }
    },
    UNAUTHORIZED {
        override fun getError(): String {
            return " Login failed, Please check your credentials and try again."
        }
    },
    CONFLICT {
        override fun getError(): String {
            return "This was changed somewhere else. The other copy was kept."
        }
    },
    TOO_MANY_REQUESTS {
        override fun getError(): String {
            return "Too many requests"
        }
    },
    NO_INTERNET {
        override fun getError(): String {
            return "Please check your internet connection."
        }
    },
    PAYLOAD_TOO_LARGE {
        override fun getError(): String {
            return "That file is too large to upload."
        }
    },
    SERVER_ERROR {
        override fun getError(): String {
            return "Something went wrong!!"
        }
    },
    SERIALIZATION {
        override fun getError(): String {
            return "Serialization"
        }
    },
    // 🔐 20-Aug-2026 sync: refresh failed / token unusable — the ONLY error that should force a logout
    SESSION_EXPIRED {
        override fun getError(): String {
            return "Your session has expired. Please sign in again."
        }
    },
    // 🔐 20-Aug-2026 sync: 403 is "not yours", not "not logged in" — logging out on it is wrong
    FORBIDDEN {
        override fun getError(): String {
            return "You do not have access to this."
        }
    },
    // 🔧 20-Aug-2026: 400 used to map to NOT_FOUND, which hid every validation failure
    BAD_REQUEST {
        override fun getError(): String {
            return "The request was rejected. Please try again."
        }
    },
    // 🔧 28-Aug-2026: 422 had no mapping at all, so a rejected sync surfaced as UNKNOWN — and
    //   UNKNOWN's message was an empty string, which is the blank "Sync failed" alert.
    VALIDATION_FAILED {
        override fun getError(): String {
            return "The server could not accept this data."
        }
    },
    UNKNOWN {
        override fun getError(): String {
            return "Something went wrong. Please try again."
        }

    };

    abstract fun getError(): String
}

enum class ValidationError : Error {

    NAME {
        override fun getError(): String = "Enter a your name"
    },
    EMAIL {
        override fun getError(): String = "Enter a valid email"
    },
    PHONE {
        override fun getError(): String = "Enter a valid phone number."
    },
    PASSWORD {
        override fun getError(): String = "Enter a strong password."
    },
    PASSWORD_NOT_MATCHED {
        override fun getError(): String ="Password did not matched"
    },
    NONE {
        override fun getError(): String ="SuccessFul"
    };
    abstract fun getError(): String
}
data class ErrorMessage(val message : String) : Error