package com.personalization.api.responses

data class ResponseResult<Response>(
    val response: Response? = null,
    val error: SDKErrorResponse? = null
)
