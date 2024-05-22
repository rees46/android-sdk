package com.personalizatio

import java.util.Map

/**
 * Created by Sergey Odintsov
 *
 * @author nixx.dj@gmail.com
 */
interface OnMessageListener {
    fun onMessage(data: Map<String?, String?>?)
}
