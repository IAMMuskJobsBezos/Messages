package org.fossify.messages.adapters

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.provider.Telephony
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.usableScreenSize
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.activities.VCardViewerActivity
import org.fossify.messages.databinding.ItemAttachmentDocumentBinding
import org.fossify.messages.databinding.ItemAttachmentImageBinding
import org.fossify.messages.databinding.ItemAttachmentVcardBinding
import org.fossify.messages.databinding.ItemMessageBinding
import org.fossify.messages.databinding.ItemThreadSendingBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.isImageMimeType
import org.fossify.messages.extensions.isVCardMimeType
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.extensions.launchViewIntent
import org.fossify.messages.helpers.EXTRA_VCARD_URI
import org.fossify.messages.helpers.THREAD_RECEIVED_MESSAGE
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_SENDING
import org.fossify.messages.helpers.generateStableId
import org.fossify.messages.helpers.setupDocumentPreview
import org.fossify.messages.helpers.setupVCardPreview
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadItem
import org.fossify.messages.models.ThreadItem.ThreadSending
import org.joda.time.DateTime

class ThreadAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
    val isRecycleBin: Boolean,
    val deleteMessages: (messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) -> Unit
) : MyRecyclerViewListAdapter<ThreadItem>(activity, recyclerView, ThreadItemDiffCallback(), itemClick) {
    private var fontSize = activity.getTextSize()
    private val maxChatBubbleWidth = (activity.usableScreenSize.x * 0.8f).toInt()

    companion object {
        private const val MAX_MEDIA_HEIGHT_RATIO = 3
        private const val MINUTE_IN_MILLIS = 60_000L
    }

    init {
        setupDragListener(true)
        setHasStableIds(true)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    override fun getActionMenuId() = R.menu.cab_thread

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = currentList.filterIsInstance<Message>().size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int): Int? {
        return (currentList.getOrNull(position) as? Message)?.getSelectionKey()
    }

    override fun getItemKeyPosition(key: Int): Int {
        return currentList.indexOfFirst { (it as? Message)?.getSelectionKey() == key }
    }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            THREAD_SENT_MESSAGE_SENDING -> ItemThreadSendingBinding.inflate(layoutInflater, parent, false)
            else -> ItemMessageBinding.inflate(layoutInflater, parent, false)
        }

        return ThreadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isClickable = item is Message
        holder.bindView(item, isClickable, allowLongClick = false) { itemView, _ ->
            when (item) {
                is ThreadSending -> setupThreadSending(itemView)
                is Message -> setupView(holder, itemView, item)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is Message -> item.getStableId()
            is ThreadSending -> generateStableId(THREAD_SENT_MESSAGE_SENDING, item.messageId)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ThreadSending -> THREAD_SENT_MESSAGE_SENDING
            is Message -> if (item.isReceivedMessage()) THREAD_RECEIVED_MESSAGE else THREAD_SENT_MESSAGE
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val question = String.format(resources.getString(org.fossify.commons.R.string.deletion_confirmation), items)

        // deleting is always permanent now, there is no recycle bin to move messages into
        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                val messagesToRemove = getSelectedItems()
                if (messagesToRemove.isNotEmpty()) {
                    deleteMessages(messagesToRemove.filterIsInstance<Message>(), false, false)
                }
            }
        }
    }

    private fun getSelectedItems(): ArrayList<ThreadItem> {
        return currentList.filter {
            selectedKeys.contains((it as? Message)?.getSelectionKey() ?: 0)
        } as ArrayList<ThreadItem>
    }

    fun updateMessages(
        newMessages: ArrayList<ThreadItem>,
        scrollPosition: Int = -1,
        smoothScroll: Boolean = false
    ) {
        val latestMessages = newMessages.toMutableList()
        submitList(latestMessages) {
            if (scrollPosition != -1) {
                if (smoothScroll) {
                    recyclerView.smoothScrollToPosition(scrollPosition)
                } else {
                    recyclerView.scrollToPosition(scrollPosition)
                }
            }
        }
    }

    private fun setupView(holder: ViewHolder, view: View, message: Message) {
        ItemMessageBinding.bind(view).apply {
            threadMessageHolder.isSelected = selectedKeys.contains(message.getSelectionKey())
            threadMessageBody.apply {
                text = message.body
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                beVisibleIf(message.body.isNotEmpty())
                setOnLongClickListener {
                    holder.viewLongClicked()
                    true
                }

                setOnClickListener {
                    holder.viewClicked(message)
                }
            }

            // Failed sends show a reload icon directly over the bubble - tapping it (or the
            // body underneath, once the overlay is gone) re-sends via handleItemClick.
            threadMessageFailedOverlay.setOnClickListener {
                holder.viewClicked(message)
            }

            if (message.isReceivedMessage()) {
                setupReceivedMessageView(messageBinding = this, message = message)
            } else {
                setupSentMessageView(holder = holder, messageBinding = this, message = message)
            }

            if (message.attachment?.attachments?.isNotEmpty() == true) {
                threadMessageAttachmentsHolder.beVisible()
                threadMessageAttachmentsHolder.removeAllViews()
                for (attachment in message.attachment.attachments) {
                    val mimetype = attachment.mimetype
                    when {
                        mimetype.isImageMimeType() || mimetype.isVideoMimeType() -> setupImageView(holder, binding = this, message, attachment)
                        mimetype.isVCardMimeType() -> setupVCardView(holder, threadMessageAttachmentsHolder, message, attachment)
                        else -> setupFileView(holder, threadMessageAttachmentsHolder, message, attachment)
                    }

                    threadMessagePlayOutline.beVisibleIf(mimetype.startsWith("video/"))
                }
            } else {
                threadMessageAttachmentsHolder.beGone()
                threadMessagePlayOutline.beGone()
            }
        }
    }

    private fun setupReceivedMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            with(ConstraintSet()) {
                clone(threadMessageHolder)
                clear(threadMessageWrapper.id, ConstraintSet.END)
                connect(threadMessageWrapper.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                applyTo(threadMessageHolder)
            }

            threadMessageBody.apply {
                background = AppCompatResources.getDrawable(activity, R.drawable.item_received_background)
                setTextColor(textColor)
                setLinkTextColor(activity.getProperPrimaryColor())
            }

            threadMessageLabel.apply {
                beVisible()
                text = "${message.senderName} | ${formatBubbleLabelTime(message)}"
                setTextColor(textColor)
            }

            threadMessageFailedOverlay.beGone()
        }
    }

    private fun setupSentMessageView(holder: ViewHolder, messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            with(ConstraintSet()) {
                clone(threadMessageHolder)
                clear(threadMessageWrapper.id, ConstraintSet.START)
                connect(threadMessageWrapper.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                applyTo(threadMessageHolder)
            }

            val primaryColor = activity.getProperPrimaryColor()
            val contrastColor = primaryColor.getContrastColor()

            threadMessageBody.apply {
                updateLayoutParams<RelativeLayout.LayoutParams> {
                    removeRule(RelativeLayout.END_OF)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }

                background = AppCompatResources.getDrawable(activity, R.drawable.item_sent_background)
                background.applyColorFilter(primaryColor)
                setTextColor(contrastColor)
                setLinkTextColor(contrastColor)

                if (message.isScheduled) {
                    typeface = Typeface.create(FontHelper.getTypeface(activity), Typeface.ITALIC)
                    val scheduledDrawable = AppCompatResources.getDrawable(activity, org.fossify.commons.R.drawable.ic_clock_vector)?.apply {
                        applyColorFilter(contrastColor)
                        val size = lineHeight
                        setBounds(0, 0, size, size)
                    }

                    setCompoundDrawables(null, null, scheduledDrawable, null)
                } else {
                    typeface = FontHelper.getTypeface(activity)
                    setCompoundDrawables(null, null, null, null)
                }
            }

            val isFailed = message.type == Telephony.Sms.MESSAGE_TYPE_FAILED

            // Always visible in the same spot - failed vs. resolved only swaps the text, so it
            // never jumps position (the label used to disappear/reappear here, which read as
            // the timestamp "moving" once a retry succeeded).
            threadMessageLabel.apply {
                beVisible()
                text = if (isFailed) activity.getString(R.string.message_not_sent_touch_retry) else formatBubbleLabelTime(message)
                setTextColor(textColor)
                updateLayoutParams<RelativeLayout.LayoutParams> {
                    removeRule(RelativeLayout.ALIGN_START)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }
                setOnClickListener { if (isFailed) holder.viewClicked(message) }
            }

            threadMessageFailedOverlay.apply {
                beVisibleIf(isFailed)
                if (isFailed) {
                    applyColorFilter(contrastColor)
                }
            }
        }
    }

    private fun formatBubbleLabelTime(message: Message): String {
        val now = DateTime.now()
        val messageTime = DateTime(message.millis())

        if (now.millis - messageTime.millis < MINUTE_IN_MILLIS) {
            return activity.getString(R.string.now_label)
        }

        val time = messageTime.toString("h:mm a")
        return when {
            now.toLocalDate() == messageTime.toLocalDate() -> time
            now.minusDays(1).toLocalDate() == messageTime.toLocalDate() -> "${activity.getString(R.string.yesterday_label)}, $time"
            else -> "${messageTime.toString("MMM d").uppercase()}, $time"
        }
    }

    private fun setupImageView(holder: ViewHolder, binding: ItemMessageBinding, message: Message, attachment: Attachment) = binding.apply {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()

        val imageView = ItemAttachmentImageBinding.inflate(layoutInflater)
        threadMessageAttachmentsHolder.addView(imageView.root)

        val placeholderDrawable = Color.TRANSPARENT.toDrawable()
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(placeholderDrawable)
            .transform(FitCenter())

        Glide.with(root.context)
            .load(uri)
            .apply(options)
            .dontAnimate()
            .override(maxChatBubbleWidth, maxChatBubbleWidth * MAX_MEDIA_HEIGHT_RATIO)
            .downsample(DownsampleStrategy.AT_MOST)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    threadMessagePlayOutline.beGone()
                    threadMessageAttachmentsHolder.removeView(imageView.root)
                    return false
                }

                override fun onResourceReady(dr: Drawable, a: Any, t: Target<Drawable>, d: DataSource, i: Boolean) = false
            })
            .into(imageView.attachmentImage)

        imageView.attachmentImage.updateLayoutParams<ViewGroup.LayoutParams> {
            width = maxChatBubbleWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        imageView.attachmentImage.setOnClickListener {
            if (actModeCallback.isSelectable) {
                holder.viewClicked(message)
            } else {
                activity.launchViewIntent(uri, mimetype, attachment.filename)
            }
        }
        imageView.root.setOnLongClickListener {
            holder.viewLongClicked()
            true
        }
    }

    private fun setupVCardView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        val vCardView = ItemAttachmentVcardBinding.inflate(layoutInflater).apply {
            setupVCardPreview(
                activity = activity,
                uri = uri,
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        val intent = Intent(activity, VCardViewerActivity::class.java).also {
                            it.putExtra(EXTRA_VCARD_URI, uri)
                        }
                        activity.startActivity(intent)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(vCardView)
    }

    private fun setupFileView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        val attachmentView = ItemAttachmentDocumentBinding.inflate(layoutInflater).apply {
            setupDocumentPreview(
                uri = uri,
                title = attachment.filename,
                mimeType = attachment.mimetype,
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        activity.launchViewIntent(uri, mimetype, attachment.filename)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(attachmentView)
    }

    private fun setupThreadSending(view: View) {
        ItemThreadSendingBinding.bind(view).threadSending.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            setTextColor(textColor)
        }
    }

    inner class ThreadViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)
}

private class ThreadItemDiffCallback : DiffUtil.ItemCallback<ThreadItem>() {

    override fun areItemsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadSending -> oldItem.messageId == (newItem as ThreadSending).messageId
            is Message -> Message.areItemsTheSame(oldItem, newItem as Message)
        }
    }

    override fun areContentsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadSending -> true
            is Message -> Message.areContentsTheSame(oldItem, newItem as Message)
        }
    }
}
