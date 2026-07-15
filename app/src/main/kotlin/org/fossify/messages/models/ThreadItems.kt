package org.fossify.messages.models

/**
 * Thread item representations for the main thread recyclerview. [Message] is also a [ThreadItem]
 */
sealed class ThreadItem {
    data class ThreadSending(val messageId: Long) : ThreadItem()
}
