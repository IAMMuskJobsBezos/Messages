package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionInfo
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FeatureLockedDialog
import org.fossify.commons.extensions.addBlockedNumber
import org.fossify.commons.extensions.addLockedLabelIfNeeded
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.darkenColor
import org.fossify.commons.extensions.getBottomNavigationBackgroundColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getMyFileUri
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.getFilenameFromUri
import org.fossify.commons.extensions.getMimeTypeFromUri
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.insetsController
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.isOrWasThankYouInstalled
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.maybeShowNumberPickerDialog
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.realScreenSize
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_READ_PHONE_STATE
import org.fossify.commons.helpers.PERMISSION_RECORD_AUDIO
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.R
import org.fossify.messages.adapters.AutoCompleteTextViewAdapter
import org.fossify.messages.adapters.ThreadAdapter
import org.fossify.messages.databinding.ActivityThreadBinding
import org.fossify.messages.databinding.ItemPendingAttachmentBinding
import org.fossify.messages.databinding.ItemSelectedContactBinding
import org.fossify.messages.databinding.ItemThreadDropdownOptionBinding
import org.fossify.messages.dialogs.InvalidNumberDialog
import org.fossify.messages.dialogs.RenameConversationDialog
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.createTemporaryThread
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.deleteMessage
import org.fossify.messages.extensions.deleteScheduledMessage
import org.fossify.messages.extensions.deleteSmsDraft
import org.fossify.messages.extensions.dialNumber
import org.fossify.messages.extensions.emptyMessagesRecycleBinForConversation
import org.fossify.messages.extensions.filterNotInByKey
import org.fossify.messages.extensions.getAddresses
import org.fossify.messages.extensions.getContactLookupUriForPhoneNumber
import org.fossify.messages.extensions.getDefaultKeyboardHeight
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.getSmsDraft
import org.fossify.messages.extensions.getThreadId
import org.fossify.messages.extensions.getThreadParticipants
import org.fossify.messages.extensions.getThreadTitle
import org.fossify.messages.extensions.indexOfFirstOrNull
import org.fossify.messages.extensions.markMessageRead
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.extensions.markThreadMessagesUnread
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.moveMessageToRecycleBin
import org.fossify.messages.extensions.onScroll
import org.fossify.messages.extensions.removeDiacriticsIfNeeded
import org.fossify.messages.extensions.renameConversation
import org.fossify.messages.extensions.restoreAllMessagesFromRecycleBinForConversation
import org.fossify.messages.extensions.restoreMessageFromRecycleBin
import org.fossify.messages.extensions.saveSmsDraft
import org.fossify.messages.extensions.shouldUnarchive
import org.fossify.messages.extensions.subscriptionManagerCompat
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.extensions.updateLastConversationMessage
import org.fossify.messages.extensions.updateScheduledMessagesThreadId
import org.fossify.messages.helpers.IS_LAUNCHED_FROM_SHORTCUT
import org.fossify.messages.helpers.IS_RECYCLE_BIN
import org.fossify.messages.helpers.MESSAGES_LIMIT
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.THREAD_ATTACHMENT_URI
import org.fossify.messages.helpers.THREAD_ATTACHMENT_URIS
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_NUMBER
import org.fossify.messages.helpers.THREAD_TEXT
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.helpers.generateRandomId
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.messaging.cancelScheduleSendPendingIntent
import org.fossify.messages.messaging.isShortCodeWithLetters
import org.fossify.messages.messaging.sendMessageCompat
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.Message
import org.fossify.messages.models.SIMCard
import org.fossify.messages.models.ThreadItem
import org.fossify.messages.models.ThreadItem.ThreadSending
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ThreadActivity : SimpleActivity() {
    private var threadId = 0L
    private var currentSIMCardIndex = 0
    private var isActivityVisible = false
    private var refreshedSinceSent = false
    private var threadItems = ArrayList<ThreadItem>()
    private var bus: EventBus? = null
    private var conversation: Conversation? = null
    private var participants = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()
    private var messages = ArrayList<Message>()
    private val availableSIMCards = ArrayList<SIMCard>()
    private var loadingOlderMessages = false
    private var allMessagesFetched = false
    private var isJumpingToMessage = false
    private var isRecycleBin = false
    private var isLaunchedFromShortcut = false

    private var messageToResend: Long? = null

    private enum class InputMode { IDLE, KEYBOARD, LISTENING }
    private var inputMode = InputMode.IDLE
    private var isVoicePillCompact = false
    private var speechRecognizer: SpeechRecognizer? = null
    private val pendingAttachmentUris = ArrayList<Uri>()

    private val binding by viewBinding(ActivityThreadBinding::inflate)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(
                binding.inputModeHolder.root,
                binding.shortCodeHolder.root
            )
        )
        setupMessagingEdgeToEdge()
        setupMaterialScrollListener(null, binding.threadAppbar)

        val extras = intent.extras
        if (extras == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            finish()
            return
        }

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        intent.getStringExtra(THREAD_TITLE)?.let {
            binding.threadToolbar.title = it
        }
        isRecycleBin = intent.getBooleanExtra(IS_RECYCLE_BIN, false)
        isLaunchedFromShortcut = intent.getBooleanExtra(IS_LAUNCHED_FROM_SHORTCUT, false)

        bus = EventBus.getDefault()
        bus!!.register(this)

        loadConversation()
        maybeSetupRecycleBinView()
        setupInputModeArea()
        setupPendingAttachmentsFromIntent()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(
            topAppBar = binding.threadAppbar,
            navigationIcon = NavigationIcon.None,
            topBarColor = getProperBackgroundColor()
        )

        isActivityVisible = true

        notificationManager.cancel(threadId.hashCode())

        ensureBackgroundThread {
            val newConv = conversationsDB.getConversationWithThreadId(threadId)
            if (newConv != null) {
                conversation = newConv
                runOnUiThread {
                    setupThreadTitle()
                }
            }

            val smsDraft = getSmsDraft(threadId)
            if (smsDraft.isNotEmpty()) {
                runOnUiThread {
                    binding.messageHolder.threadTypeMessage.setText(smsDraft)
                    binding.messageHolder.threadTypeMessage.setSelection(smsDraft.length)
                }
            }

            markThreadMessagesRead(threadId)
        }

        val bottomBarColor = getBottomBarColor()
        binding.messageHolder.root.setBackgroundColor(bottomBarColor)
        binding.shortCodeHolder.root.setBackgroundColor(bottomBarColor)
        binding.inputModeHolder.root.setBackgroundColor(bottomBarColor)
    }

    override fun onPause() {
        super.onPause()
        saveDraftMessage()
        bus?.post(Events.RefreshConversations())
        isActivityVisible = false
    }

    override fun onStop() {
        super.onStop()
        saveDraftMessage()
    }

    override fun onBackPressedCompat(): Boolean {
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun saveDraftMessage() {
        val draftMessage = binding.messageHolder.threadTypeMessage.value
        ensureBackgroundThread {
            if (draftMessage.isNotEmpty()) {
                saveSmsDraft(draftMessage, threadId)
            } else {
                deleteSmsDraft(threadId)
            }
        }
    }

    private fun refreshMenuItems() {
        // the header intentionally shows only the title and the three dots button,
        // everything else lives in the pill dropdown (see showHeaderDropdown)
        binding.threadToolbar.menu.findItem(R.id.more_options).isVisible = !isRecycleBin
    }

    private fun setupOptionsMenu() {
        binding.threadToolbar.setOnMenuItemClickListener { menuItem ->
            if (participants.isEmpty()) return@setOnMenuItemClickListener true
            return@setOnMenuItemClickListener handleMenuItemAction(menuItem)
        }

        // tapping anywhere on the header (not just the three dots icon) opens the dropdown
        binding.threadToolbar.setOnClickListener {
            if (participants.isNotEmpty() && !isRecycleBin) {
                toggleHeaderDropdown()
            }
        }

        // tapping outside the dropdown dismisses it
        binding.threadHeaderDropdownScrim.setOnClickListener {
            hideHeaderDropdown()
        }
    }

    private fun handleMenuItemAction(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.more_options -> toggleHeaderDropdown()
            else -> return false
        }

        return true
    }

    private fun toggleHeaderDropdown() {
        if (binding.threadHeaderDropdown.isVisible()) {
            hideHeaderDropdown()
        } else {
            showHeaderDropdown()
        }
    }

    private fun hideHeaderDropdown() {
        binding.threadHeaderDropdown.beGone()
        binding.threadHeaderDropdown.removeAllViews()
        binding.threadHeaderDropdownScrim.beGone()
    }

    private fun showHeaderDropdown() {
        binding.threadHeaderDropdown.removeAllViews()
        if (participants.isEmpty()) {
            return
        }


        if (participants.size == 1) {
            val participant = participants.first()
            val isSavedContact = participant.name != participant.phoneNumbers.first().value &&
                participant.name != participant.phoneNumbers.first().normalizedNumber
            addDropdownOption(org.fossify.commons.R.drawable.ic_phone_vector, getString(org.fossify.commons.R.string.call)) {
                dialNumber()
            }
            if (isSavedContact) {
                addDropdownOption(org.fossify.commons.R.drawable.ic_person_vector, getString(R.string.view_contact)) {
                    viewContact()
                }
                addDropdownOption(org.fossify.commons.R.drawable.ic_edit_vector, getString(R.string.edit_contact)) {
                    editContact()
                }
            } else {
                addDropdownOption(org.fossify.commons.R.drawable.ic_add_person_vector, getString(R.string.add_to_contacts)) {
                    addNumberToContact(participant.phoneNumbers.first().normalizedNumber)
                }
            }
        } else {
            // group conversations pick the person in a second step, like the wireframe
            addDropdownOption(org.fossify.commons.R.drawable.ic_phone_vector, getString(org.fossify.commons.R.string.call)) {
                showNumberChoices(org.fossify.commons.R.drawable.ic_phone_vector) { number ->
                    dialNumber(number)
                }
            }
            addDropdownOption(org.fossify.commons.R.drawable.ic_add_person_vector, getString(R.string.add_to_contacts)) {
                showNumberChoices(org.fossify.commons.R.drawable.ic_add_person_vector) { number ->
                    addNumberToContact(number)
                }
            }
        }
        binding.threadHeaderDropdown.beVisible()
        binding.threadHeaderDropdownScrim.beVisible()
    }

    private fun showNumberChoices(iconId: Int, onPick: (String) -> Unit) {
        binding.threadHeaderDropdown.removeAllViews()
        participants.forEach { participant ->
            val number = participant.phoneNumbers.first().normalizedNumber
            addDropdownOption(iconId, participant.name, hideOnClick = true) {
                onPick(number)
            }
        }
        binding.threadHeaderDropdown.beVisible()
        binding.threadHeaderDropdownScrim.beVisible()
    }

    private fun addDropdownOption(iconId: Int, label: String, hideOnClick: Boolean = true, onClick: () -> Unit) {
        val properPrimaryColor = getProperPrimaryColor()
        val contrastColor = properPrimaryColor.getContrastColor()
        ItemThreadDropdownOptionBinding.inflate(layoutInflater, binding.threadHeaderDropdown, false).apply {
            root.background.applyColorFilter(properPrimaryColor)
            dropdownOptionIcon.setImageDrawable(
                AppCompatResources.getDrawable(this@ThreadActivity, iconId)
            )
            dropdownOptionIcon.applyColorFilter(contrastColor)
            dropdownOptionLabel.text = label
            dropdownOptionLabel.setTextColor(contrastColor)
            root.setOnClickListener {
                if (hideOnClick) {
                    hideHeaderDropdown()
                }
                onClick()
            }
            binding.threadHeaderDropdown.addView(root)
        }
    }

    private fun viewContact() {
        val number = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: return
        ensureBackgroundThread {
            val uri = getContactLookupUriForPhoneNumber(number) ?: return@ensureBackgroundThread
            runOnUiThread {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                    launchActivityIntent(this)
                }
            }
        }
    }

    private fun editContact() {
        val number = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: return
        ensureBackgroundThread {
            val uri = getContactLookupUriForPhoneNumber(number) ?: return@ensureBackgroundThread
            runOnUiThread {
                Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                    launchActivityIntent(this)
                }
            }
        }
    }

    private fun setupCachedMessages(callback: () -> Unit) {
        ensureBackgroundThread {
            messages = try {
                if (isRecycleBin) {
                    messagesDB.getThreadMessagesFromRecycleBin(threadId)
                } else {
                    if (config.useRecycleBin) {
                        messagesDB.getNonRecycledThreadMessages(threadId)
                    } else {
                        messagesDB.getThreadMessages(threadId)
                    }
                }.toMutableList() as ArrayList<Message>
            } catch (e: Exception) {
                ArrayList()
            }
            clearExpiredScheduledMessages(threadId, messages)
            messages.removeAll { it.isScheduled && it.millis() < System.currentTimeMillis() }

            messages.sortBy { it.date }
            if (messages.size > MESSAGES_LIMIT) {
                messages = ArrayList(messages.takeLast(MESSAGES_LIMIT))
            }

            setupParticipants()
            setupAdapter()

            runOnUiThread {
                if (messages.isEmpty() && !isSpecialNumber()) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    binding.messageHolder.threadTypeMessage.requestFocus()
                }

                setupThreadTitle()
                setupSIMSelector()
                updateMessageType()
                callback()
            }
        }
    }

    private fun setupThread(callback: () -> Unit) {
        if (conversation == null && isLaunchedFromShortcut) {
            if (isTaskRoot) {
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(this)
                }
            }
            finish()
            return
        }
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)

            val cachedMessagesCode = messages.clone().hashCode()
            if (!isRecycleBin) {
                messages = getMessages(threadId)
                if (config.useRecycleBin) {
                    val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId)
                    messages = messages.filterNotInByKey(recycledMessages) { it.getStableId() }
                }
            }

            val hasParticipantWithoutName = participants.any { contact ->
                contact.phoneNumbers.map { it.normalizedNumber }.contains(contact.name)
            }

            try {
                if (participants.isNotEmpty() && messages.hashCode() == cachedMessagesCode && !hasParticipantWithoutName) {
                    setupAdapter()
                    runOnUiThread { callback() }
                    return@ensureBackgroundThread
                }
            } catch (ignored: Exception) {
            }

            setupParticipants()

            // check if no participant came from a privately stored contact in Simple Contacts
            if (privateContacts.isNotEmpty()) {
                val senderNumbersToReplace = HashMap<String, String>()
                participants.filter { it.doesHavePhoneNumber(it.name) }.forEach { participant ->
                    privateContacts.firstOrNull { it.doesHavePhoneNumber(participant.phoneNumbers.first().normalizedNumber) }
                        ?.apply {
                            senderNumbersToReplace[participant.phoneNumbers.first().normalizedNumber] =
                                name
                            participant.name = name
                            participant.photoUri = photoUri
                        }
                }

                messages.forEach { message ->
                    if (senderNumbersToReplace.keys.contains(message.senderName)) {
                        message.senderName = senderNumbersToReplace[message.senderName]!!
                    }
                }
            }

            if (participants.isEmpty()) {
                val name = intent.getStringExtra(THREAD_TITLE) ?: ""
                val number = intent.getStringExtra(THREAD_NUMBER)
                if (number == null) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    finish()
                    return@ensureBackgroundThread
                }

                val phoneNumber = PhoneNumber(number, 0, "", number)
                val contact = SimpleContact(
                    rawId = 0,
                    contactId = 0,
                    name = name,
                    photoUri = "",
                    phoneNumbers = arrayListOf(phoneNumber),
                    birthdays = ArrayList(),
                    anniversaries = ArrayList()
                )
                participants.add(contact)
            }

            if (!isRecycleBin) {
                messages.chunked(30).forEach { currentMessages ->
                    messagesDB.insertMessages(*currentMessages.toTypedArray())
                }
            }

            setupAdapter()
            runOnUiThread {
                setupThreadTitle()
                setupSIMSelector()
                callback()
            }
        }
    }

    private fun getOrCreateThreadAdapter(): ThreadAdapter {
        var currAdapter = binding.threadMessagesList.adapter
        if (currAdapter == null) {
            currAdapter = ThreadAdapter(
                activity = this,
                recyclerView = binding.threadMessagesList,
                itemClick = { handleItemClick(it) },
                isRecycleBin = isRecycleBin,
                deleteMessages = { messages, toRecycleBin, fromRecycleBin ->
                    deleteMessages(
                        messages,
                        toRecycleBin,
                        fromRecycleBin
                    )
                }
            )

            binding.threadMessagesList.adapter = currAdapter
        }
        return currAdapter as ThreadAdapter
    }

    private fun setupAdapter() {
        threadItems = getThreadItems()

        runOnUiThread {
            refreshMenuItems()
            getOrCreateThreadAdapter().apply {
                val layoutManager = binding.threadMessagesList.layoutManager as LinearLayoutManager
                val lastPosition = itemCount - 1
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val shouldScrollToBottom =
                    currentList.lastOrNull() != threadItems.lastOrNull() && lastPosition - lastVisiblePosition == 1
                updateMessages(threadItems, if (shouldScrollToBottom) lastPosition else -1)
            }
        }

        SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
            contacts.addAll(privateContacts)
            runOnUiThread {
                val adapter = AutoCompleteTextViewAdapter(this, contacts)
                binding.addContactOrNumber.setAdapter(adapter)
                binding.addContactOrNumber.imeOptions = EditorInfo.IME_ACTION_NEXT
                binding.addContactOrNumber.setOnItemClickListener { _, _, position, _ ->
                    val currContacts =
                        (binding.addContactOrNumber.adapter as AutoCompleteTextViewAdapter).resultList
                    val selectedContact = currContacts[position]
                    maybeShowNumberPickerDialog(selectedContact.phoneNumbers) { phoneNumber ->
                        val contactWithSelectedNumber = selectedContact.copy(
                            phoneNumbers = arrayListOf(phoneNumber)
                        )
                        addSelectedContact(contactWithSelectedNumber)
                    }
                }

                binding.addContactOrNumber.onTextChangeListener {
                    binding.confirmInsertedNumber.beVisibleIf(it.length > 2)
                }
            }
        }

        runOnUiThread {
            binding.confirmInsertedNumber.setOnClickListener {
                val number = binding.addContactOrNumber.value
                val phoneNumber = PhoneNumber(number, 0, "", number)
                val contact = SimpleContact(
                    rawId = number.hashCode(),
                    contactId = number.hashCode(),
                    name = number,
                    photoUri = "",
                    phoneNumbers = arrayListOf(phoneNumber),
                    birthdays = ArrayList(),
                    anniversaries = ArrayList()
                )
                addSelectedContact(contact)
            }
        }
    }

    private fun scrollToBottom() {
        val position = getOrCreateThreadAdapter().currentList.lastIndex
        if (position >= 0) {
            binding.threadMessagesList.smoothScrollToPosition(position)
        }
    }

    private fun setupScrollListener() {
        binding.threadMessagesList.onScroll(
            onScrolled = { dx, dy ->
                tryLoadMoreMessages()
                val layoutManager = binding.threadMessagesList.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val isCloseToBottom =
                    lastVisibleItemPosition >= getOrCreateThreadAdapter().itemCount - SCROLL_TO_BOTTOM_FAB_LIMIT
                val fab = binding.scrollToBottomFab
                if (isCloseToBottom) fab.hide() else fab.show()
            },
            onScrollStateChanged = { newState ->
                if (newState == RecyclerView.SCROLL_STATE_IDLE) tryLoadMoreMessages()
            }
        )
    }

    private fun handleItemClick(any: Any) {
        when {
            // tap-to-retry: the reload icon overlaid on a failed bubble re-sends immediately
            any is Message && any.type == Telephony.Sms.MESSAGE_TYPE_FAILED -> {
                binding.messageHolder.threadTypeMessage.setText(any.body)
                messageToResend = any.id
                sendMessage()
            }
        }
    }

    private fun deleteMessages(
        messagesToRemove: List<Message>,
        toRecycleBin: Boolean,
        fromRecycleBin: Boolean,
    ) {
        val deletePosition = threadItems.indexOf(messagesToRemove.first())
        messages.removeAll(messagesToRemove.toSet())
        threadItems = getThreadItems()

        runOnUiThread {
            if (messages.isEmpty()) {
                finish()
            } else {
                getOrCreateThreadAdapter().apply {
                    updateMessages(threadItems, scrollPosition = deletePosition)
                    finishActMode()
                }
            }
        }

        messagesToRemove.forEach { message ->
            val messageId = message.id
            if (message.isScheduled) {
                deleteScheduledMessage(messageId)
                cancelScheduleSendPendingIntent(messageId)
            } else {
                if (toRecycleBin) {
                    moveMessageToRecycleBin(messageId)
                } else if (fromRecycleBin) {
                    restoreMessageFromRecycleBin(messageId)
                } else {
                    deleteMessage(messageId, message.isMMS)
                }
            }
        }
        updateLastConversationMessage(threadId)

        // move all scheduled messages to a temporary thread when there are no real messages left
        if (messages.isNotEmpty() && messages.all { it.isScheduled }) {
            val scheduledMessage = messages.last()
            val fakeThreadId = generateRandomId()
            createTemporaryThread(scheduledMessage, fakeThreadId, conversation)
            updateScheduledMessagesThreadId(messages, fakeThreadId)
            threadId = fakeThreadId
        }
    }

    private fun jumpToMessage(messageId: Long) {
        if (messages.any { it.id == messageId }) {
            val index = threadItems.indexOfFirst { (it as? Message)?.id == messageId }
            if (index != -1) binding.threadMessagesList.smoothScrollToPosition(index)
            return
        }

        ensureBackgroundThread {
            if (loadingOlderMessages) return@ensureBackgroundThread
            loadingOlderMessages = true
            isJumpingToMessage = true

            var cutoff = messages.firstOrNull()?.date ?: Int.MAX_VALUE
            var found = false
            var loops = 0

            // not the best solution, but this will do for now.
            while (!found && !allMessagesFetched) {
                if (fetchOlderMessages(cutoff).isEmpty() || loops >= 1000) break
                cutoff = messages.first().date
                found = messages.any { it.id == messageId }
                loops++
            }

            threadItems = getThreadItems()
            runOnUiThread {
                loadingOlderMessages = false
                val index = threadItems.indexOfFirst { (it as? Message)?.id == messageId }
                getOrCreateThreadAdapter().updateMessages(
                    newMessages = threadItems, scrollPosition = index, smoothScroll = true
                )
                isJumpingToMessage = false
            }
        }
    }

    private fun tryLoadMoreMessages() {
        if (isJumpingToMessage) return
        val layoutManager = binding.threadMessagesList.layoutManager as LinearLayoutManager
        if (layoutManager.findFirstVisibleItemPosition() <= PREFETCH_THRESHOLD) {
            loadMoreMessages()
        }
    }

    private fun loadMoreMessages() {
        if (messages.isEmpty() || allMessagesFetched || loadingOlderMessages) return
        loadingOlderMessages = true
        val cutoff = messages.first().date
        ensureBackgroundThread {
            fetchOlderMessages(cutoff)
            threadItems = getThreadItems()
            runOnUiThread {
                loadingOlderMessages = false
                getOrCreateThreadAdapter().updateMessages(threadItems)
            }
        }
    }

    private fun fetchOlderMessages(cutoff: Int): List<Message> {
        val older = getMessages(threadId, cutoff)
            .filterNotInByKey(messages) { it.getStableId() }

        if (older.isEmpty()) {
            allMessagesFetched = true
            return older
        }

        messages.addAll(0, older)
        return older
    }

    private fun loadConversation() {
        handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
            if (granted) {
                setupButtons()
                setupConversation()
                setupCachedMessages {
                    setupThread {
                        val searchedMessageId = intent.getLongExtra(SEARCHED_MESSAGE_ID, -1L)
                        intent.removeExtra(SEARCHED_MESSAGE_ID)
                        if (searchedMessageId != -1L) {
                            jumpToMessage(searchedMessageId)
                        }
                    }
                    setupScrollListener()
                }
            } else {
                finish()
            }
        }
    }

    private fun setupConversation() {
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
        }
    }

    private fun setupButtons() = binding.apply {
        updateTextColors(threadHolder)
        val textColor = getProperTextColor()

        binding.messageHolder.apply {
            val properPrimaryColor = getProperPrimaryColor()

            // SEND button and the message field share the chips' rectangle style
            threadSendMessage.background = AppCompatResources.getDrawable(
                this@ThreadActivity, R.drawable.chip_rectangle_background
            )
            threadSendMessage.background.applyColorFilter(properPrimaryColor)
            threadSendMessage.setTextColor(properPrimaryColor.getContrastColor())

            threadTypeMessage.background = AppCompatResources.getDrawable(
                this@ThreadActivity, R.drawable.chip_rectangle_background
            )
            threadTypeMessage.background.applyColorFilter(properPrimaryColor.adjustAlpha(0.25f))

            // the top line blends into the bar's own light grey background
            sendBarTopDivider.setBackgroundColor(getBottomBarColor())

            confirmManageContacts.applyColorFilter(textColor)

            threadMessagesFastscroller.updateColors(properPrimaryColor)

            threadCharacterCounter.beVisibleIf(config.showCharacterCounter)
            threadCharacterCounter.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize())

            threadTypeMessage.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize())
            threadSendMessage.setOnClickListener {
                sendMessage()
            }

            threadSendMessage.isClickable = false
            threadTypeMessage.onTextChangeListener {
                messageToResend = null
                checkSendMessageAvailability()
                updateVoicePillLayout(hasText = it.isNotEmpty())
                val messageString = if (config.useSimpleCharacters) {
                    it.normalizeString()
                } else {
                    it
                }
                val messageLength = SmsMessage.calculateLength(messageString, false)
                @SuppressLint("SetTextI18n")
                threadCharacterCounter.text = "${messageLength[2]}/${messageLength[0]}"
            }

            if (config.sendOnEnter) {
                threadTypeMessage.inputType = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
                threadTypeMessage.imeOptions = EditorInfo.IME_ACTION_SEND
                threadTypeMessage.setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_SEND) {
                        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                        return@setOnEditorActionListener true
                    }
                    false
                }

                threadTypeMessage.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                        sendMessage()
                        return@setOnKeyListener true
                    }
                    false
                }
            }

            confirmManageContacts.setOnClickListener {
                hideKeyboard()
                threadAddContacts.beGone()

                val numbers = HashSet<String>()
                participants.forEach { contact ->
                    contact.phoneNumbers.forEach {
                        numbers.add(it.normalizedNumber)
                    }
                }

                val newThreadId = getThreadId(numbers)
                if (threadId != newThreadId) {
                    hideKeyboard()
                    Intent(this@ThreadActivity, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, newThreadId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(this)
                    }
                }
            }

            threadTypeMessage.setText(intent.getStringExtra(THREAD_TEXT))
            scrollToBottomFab.setOnClickListener {
                scrollToBottom()
            }
            scrollToBottomFab.backgroundTintList = ColorStateList.valueOf(getBottomBarColor())
            scrollToBottomFab.applyColorFilter(textColor)
        }
    }

    private fun setupInputModeArea() = binding.inputModeHolder.apply {
        val properPrimaryColor = getProperPrimaryColor()
        val contrastColor = properPrimaryColor.getContrastColor()

        // Idle row + the two small pills are outlined (transparent fill, primary-color stroke)
        // rather than filled, so they don't add blocks of solid primary color everywhere - and
        // it gives them somewhere to go (filled) as press feedback.
        listOf(inputModeVoiceButton, inputModeKeyboardButton, inputModeVoicePill, inputModeListeningKeyboardPill).forEach {
            it.background.applyColorFilter(properPrimaryColor)
        }
        listOf(inputModeVoiceIcon, inputModeKeyboardIcon, inputModeVoicePillIcon, inputModeListeningKeyboardIcon).forEach {
            it.applyColorFilter(properPrimaryColor)
        }
        listOf(inputModeVoiceLabel, inputModeKeyboardLabel, inputModeVoicePillLabel, inputModeListeningKeyboardLabel).forEach {
            it.setTextColor(properPrimaryColor)
        }

        // Stop button stays filled by default (it's mid-recording, needs to stand out).
        inputModeStopButton.background.applyColorFilter(properPrimaryColor)
        inputModeStopIcon.applyColorFilter(contrastColor)
        inputModeStopLabel.setTextColor(contrastColor)

        inputModeListeningLabel.setTextColor(getProperTextColor())

        inputModeVoiceButton.setOnClickListener { startListening() }
        inputModeKeyboardButton.setOnClickListener { showKeyboardMode() }
        inputModeVoicePill.setOnClickListener { startListening() }
        inputModeListeningKeyboardPill.setOnClickListener { stopListening() }
        inputModeStopButton.setOnClickListener { stopListening() }

        // Outlined by default, fills solid while actually pressed down - press feedback.
        setupOutlineToFillPress(inputModeVoiceButton, inputModeVoiceIcon, inputModeVoiceLabel, properPrimaryColor, contrastColor)
        setupOutlineToFillPress(inputModeKeyboardButton, inputModeKeyboardIcon, inputModeKeyboardLabel, properPrimaryColor, contrastColor)
        setupOutlineToFillPress(
            inputModeVoicePill, inputModeVoicePillIcon, inputModeVoicePillLabel, properPrimaryColor, contrastColor,
            outlineRes = R.drawable.button_rounded_background_outline, filledRes = R.drawable.button_rounded_background
        )
        setupOutlineToFillPress(
            inputModeListeningKeyboardPill, inputModeListeningKeyboardIcon, inputModeListeningKeyboardLabel, properPrimaryColor, contrastColor,
            outlineRes = R.drawable.button_rounded_background_outline, filledRes = R.drawable.button_rounded_background
        )

        // Stop button: mirror image of the above - filled by default (it's mid-recording), and
        // turns outlined while actually pressed down as feedback that tapping it is stopping.
        setupFillToOutlinePress(inputModeStopButton, inputModeStopIcon, inputModeStopLabel, properPrimaryColor, contrastColor)

        binding.messageHolder.threadTypeMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && inputMode != InputMode.LISTENING) {
                setInputMode(InputMode.KEYBOARD)
            }
        }

        // Two-way sync with the IME: the focus listener above catches most cases, but if the
        // EditText already had focus when the keyboard was raised (e.g. auto-focused on
        // opening an empty thread), no focus *change* fires and the idle Voice/Keyboard row
        // would otherwise be left showing underneath the system keyboard. Insets are the one
        // signal that's reliable regardless of how the keyboard was triggered.
        ViewCompat.setOnApplyWindowInsetsListener(binding.threadHolder) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible && inputMode == InputMode.IDLE) {
                setInputMode(InputMode.KEYBOARD)
            } else if (!imeVisible && inputMode == InputMode.KEYBOARD) {
                setInputMode(InputMode.IDLE)
            }
            insets
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOutlineToFillPress(
        button: View,
        icon: ImageView,
        label: TextView,
        primaryColor: Int,
        contrastColor: Int,
        outlineRes: Int = R.drawable.button_rounded_rect_background,
        filledRes: Int = R.drawable.button_rounded_rect_background_filled
    ) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = AppCompatResources.getDrawable(this, filledRes)
                    v.background.applyColorFilter(primaryColor)
                    icon.applyColorFilter(contrastColor)
                    label.setTextColor(contrastColor)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.background = AppCompatResources.getDrawable(this, outlineRes)
                    v.background.applyColorFilter(primaryColor)
                    icon.applyColorFilter(primaryColor)
                    label.setTextColor(primaryColor)
                }
            }
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFillToOutlinePress(button: View, icon: ImageView, label: TextView, primaryColor: Int, contrastColor: Int) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = AppCompatResources.getDrawable(this, R.drawable.button_rounded_rect_background)
                    v.background.applyColorFilter(primaryColor)
                    icon.applyColorFilter(primaryColor)
                    label.setTextColor(primaryColor)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.background = AppCompatResources.getDrawable(this, R.drawable.button_rounded_rect_background_filled)
                    v.background.applyColorFilter(primaryColor)
                    icon.applyColorFilter(contrastColor)
                    label.setTextColor(contrastColor)
                }
            }
            false
        }
    }

    private fun setInputMode(mode: InputMode) {
        inputMode = mode
        binding.inputModeHolder.apply {
            inputModeIdleHolder.beVisibleIf(mode == InputMode.IDLE)
            inputModeVoicePill.beVisibleIf(mode == InputMode.KEYBOARD)
            inputModeListeningHolder.beVisibleIf(mode == InputMode.LISTENING)
        }
        if (mode == InputMode.KEYBOARD) {
            updateVoicePillLayout(hasText = binding.messageHolder.threadTypeMessage.value.isNotEmpty())
        }
    }

    // Empty field: big pill centered above the keyboard, same look as Idle's "Voice to text".
    // Once typing starts (the system keyboard's own suggestion strip appears), it shrinks down
    // to a small button on the right so it doesn't compete with the suggestions, per the
    // original wireframe ("'lly' | loopy | lego | (mic) Voice").
    private fun updateVoicePillLayout(hasText: Boolean) = binding.inputModeHolder.apply {
        if (isVoicePillCompact == hasText) {
            return@apply
        }
        isVoicePillCompact = hasText

        with(ConstraintSet()) {
            clone(root)
            if (hasText) {
                clear(inputModeVoicePill.id, ConstraintSet.START)
                connect(inputModeVoicePill.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            } else {
                connect(inputModeVoicePill.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                connect(inputModeVoicePill.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            }
            applyTo(root)
        }

        val horizontalPaddingRes = if (hasText) org.fossify.commons.R.dimen.normal_margin else org.fossify.commons.R.dimen.activity_margin
        val verticalPaddingRes = if (hasText) org.fossify.commons.R.dimen.tiny_margin else org.fossify.commons.R.dimen.normal_margin
        val extraHorizontalPadding = (6 * resources.displayMetrics.density).toInt()
        val horizontalPadding = resources.getDimensionPixelSize(horizontalPaddingRes) + extraHorizontalPadding
        val verticalPadding = resources.getDimensionPixelSize(verticalPaddingRes)
        inputModeVoicePill.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
    }

    private fun showKeyboardMode() {
        setInputMode(InputMode.KEYBOARD)
        binding.messageHolder.threadTypeMessage.requestFocus()
        window.insetsController(binding.messageHolder.threadTypeMessage).show(WindowInsetsCompat.Type.ime())
    }

    // Photos shared into the app from elsewhere (Android share sheet -> NewConversationActivity
    // -> here) land as THREAD_ATTACHMENT_URI(S) extras. This is the only attachment path left -
    // there is no "+" picker anymore, just showing/sending what was already shared in.
    private fun setupPendingAttachmentsFromIntent() {
        if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
            intent.getStringExtra(THREAD_ATTACHMENT_URI)?.toUri()?.let { addPendingAttachment(it) }
        } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
            intent.getParcelableArrayListExtra<Uri>(THREAD_ATTACHMENT_URIS)?.forEach { addPendingAttachment(it) }
        }
    }

    private fun addPendingAttachment(uri: Uri) {
        pendingAttachmentUris.add(uri)

        val thumbnailBinding = ItemPendingAttachmentBinding.inflate(
            layoutInflater, binding.messageHolder.pendingAttachmentsHolder, false
        )
        thumbnailBinding.root.tag = uri
        val cornerRadius = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.rounded_corner_radius_small)
        Glide.with(this)
            .load(uri)
            .transform(CenterCrop(), RoundedCorners(cornerRadius))
            .into(thumbnailBinding.pendingAttachmentImage)
        thumbnailBinding.pendingAttachmentPlayBadge.beVisibleIf(getMimeTypeFromUri(uri).startsWith("video/"))

        thumbnailBinding.pendingAttachmentRemove.applyColorFilter(getProperBackgroundColor())
        thumbnailBinding.pendingAttachmentRemove.background.applyColorFilter(getProperTextColor())
        thumbnailBinding.pendingAttachmentRemove.setOnClickListener {
            removePendingAttachment(uri, thumbnailBinding.root)
        }

        binding.messageHolder.pendingAttachmentsHolder.addView(thumbnailBinding.root)
        binding.messageHolder.pendingAttachmentsHolder.beVisible()
        checkSendMessageAvailability()
    }

    private fun removePendingAttachment(uri: Uri, view: View) {
        pendingAttachmentUris.remove(uri)
        binding.messageHolder.pendingAttachmentsHolder.removeView(view)
        binding.messageHolder.pendingAttachmentsHolder.beVisibleIf(pendingAttachmentUris.isNotEmpty())
        checkSendMessageAvailability()
    }

    private fun clearPendingAttachments() {
        pendingAttachmentUris.clear()
        binding.messageHolder.pendingAttachmentsHolder.removeAllViews()
        binding.messageHolder.pendingAttachmentsHolder.beGone()
    }

    private fun buildPendingAttachments(messageId: Long): ArrayList<Attachment> {
        val result = ArrayList<Attachment>()
        for (uri in pendingAttachmentUris) {
            val mimetype = getMimeTypeFromUri(uri).ifEmpty { "image/*" }
            val filename = getFilenameFromUri(uri)
            var width = 0
            var height = 0
            try {
                contentResolver.openInputStream(uri)?.use {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(it, null, options)
                    width = options.outWidth
                    height = options.outHeight
                }
            } catch (_: Exception) {
            }

            result.add(
                Attachment(
                    id = null,
                    messageId = messageId,
                    uriString = uri.toString(),
                    mimetype = mimetype,
                    width = width,
                    height = height,
                    filename = filename
                )
            )
        }
        return result
    }

    // "Voice to text" hands recognition off to the system's own SpeechRecognizer; we only draw
    // the Listening/Stop chrome around it, per the wireframe.
    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast(R.string.voice_input_unavailable)
            return
        }

        handlePermission(PERMISSION_RECORD_AUDIO) { granted ->
            if (granted) {
                hideKeyboard()
                setInputMode(InputMode.LISTENING)
                createSpeechRecognizerIfNeeded().startListening(getSpeechRecognizerIntent())
            }
        }
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        showKeyboardMode()
    }

    private fun getSpeechRecognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
    }

    private fun createSpeechRecognizerIfNeeded(): SpeechRecognizer {
        speechRecognizer?.let { return it }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                appendRecognizedText(matches?.firstOrNull())
                if (inputMode == InputMode.LISTENING) {
                    showKeyboardMode()
                }
            }

            override fun onError(error: Int) {
                if (inputMode == InputMode.LISTENING) {
                    showKeyboardMode()
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer = recognizer
        return recognizer
    }

    private fun appendRecognizedText(text: String?) {
        if (text.isNullOrEmpty()) {
            return
        }

        val field = binding.messageHolder.threadTypeMessage
        val existing = field.value
        val combined = if (existing.isEmpty()) text else "$existing $text"
        field.setText(combined)
        field.setSelection(combined.length)
    }

    private fun setupParticipants() {
        if (participants.isEmpty()) {
            participants = if (messages.isEmpty()) {
                val intentNumbers = getPhoneNumbersFromIntent()
                val participants = getThreadParticipants(threadId, null)
                fixParticipantNumbers(participants, intentNumbers)
            } else {
                messages.first().participants
            }
            runOnUiThread {
                maybeDisableShortCodeReply()
            }
        }
    }

    private fun isSpecialNumber(): Boolean {
        val addresses = participants.getAddresses()
        return addresses.any { isShortCodeWithLetters(it) }
    }

    private fun maybeDisableShortCodeReply() {
        if (isSpecialNumber() && !isRecycleBin) {
            currentFocus?.clearFocus()
            hideKeyboard()
            binding.messageHolder.threadTypeMessage.text?.clear()
            binding.messageHolder.root.beGone()
            binding.shortCodeHolder.root.beVisible()
            val textColor = getProperTextColor()
            binding.shortCodeHolder.replyDisabledText.setTextColor(textColor)
            binding.shortCodeHolder.replyDisabledInfo.apply {
                applyColorFilter(textColor)
                setOnClickListener {
                    InvalidNumberDialog(
                        activity = this@ThreadActivity,
                        text = getString(R.string.invalid_short_code_desc)
                    )
                }
                tooltipText = getString(org.fossify.commons.R.string.more_info)
            }
        }
    }

    private fun setupThreadTitle() {
        // stored group titles contain every participant, so only a title the user
        // typed themselves may override the compact "first name, +N more" form
        val customTitle = conversation?.takeIf { it.usesCustomTitle }?.title
        binding.threadToolbar.title = if (!customTitle.isNullOrEmpty()) {
            customTitle
        } else if (participants.isNotEmpty()) {
            buildCompactThreadTitle()
        } else {
            conversation?.title ?: binding.threadToolbar.title
        }
    }

    // fit as many participants as the toolbar can fully show, then "+N more"
    private fun buildCompactThreadTitle(): String {
        val names = participants.map { it.name }
        if (names.size <= 1) {
            return participants.getThreadTitle()
        }

        val paint = TextPaint().apply {
            textSize = resources.getDimension(org.fossify.commons.R.dimen.actionbar_text_size)
        }
        // toolbar width minus start inset and the three dots button
        val availableWidth = resources.displayMetrics.widthPixels -
                resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_icon_size) * 2

        for (count in names.size downTo 1) {
            val remaining = names.size - count
            val candidate = names.take(count).joinToString(", ") +
                    if (remaining > 0) ", +$remaining more" else ""
            if (paint.measureText(candidate) <= availableWidth || count == 1) {
                return candidate
            }
        }
        return names.first()
    }

    @SuppressLint("MissingPermission")
    private fun setupSIMSelector() {
        val availableSIMs = subscriptionManagerCompat().activeSubscriptionInfoList ?: return
        if (availableSIMs.size > 1) {
            availableSIMs.forEachIndexed { index, subscriptionInfo ->
                var label = subscriptionInfo.displayName?.toString() ?: ""
                if (subscriptionInfo.number?.isNotEmpty() == true) {
                    label += " (${subscriptionInfo.number})"
                }
                val simCard = SIMCard(index + 1, subscriptionInfo.subscriptionId, label)
                availableSIMCards.add(simCard)
            }

            val numbers = ArrayList<String>()
            participants.forEach { contact ->
                contact.phoneNumbers.forEach {
                    numbers.add(it.normalizedNumber)
                }
            }

            if (numbers.isEmpty()) {
                return
            }

            currentSIMCardIndex = getProperSimIndex(availableSIMs, numbers)
            binding.messageHolder.threadSelectSimIcon.applyColorFilter(getProperTextColor())
            binding.messageHolder.threadSelectSimIcon.beVisible()
            binding.messageHolder.threadSelectSimNumber.beVisible()

            if (availableSIMCards.isNotEmpty()) {
                binding.messageHolder.threadSelectSimIcon.setOnClickListener {
                    currentSIMCardIndex = (currentSIMCardIndex + 1) % availableSIMCards.size
                    val currentSIMCard = availableSIMCards[currentSIMCardIndex]
                    @SuppressLint("SetTextI18n")
                    binding.messageHolder.threadSelectSimNumber.text = currentSIMCard.id.toString()
                    val currentSubscriptionId = currentSIMCard.subscriptionId
                    numbers.forEach {
                        config.saveUseSIMIdAtNumber(it, currentSubscriptionId)
                    }
                    toast(currentSIMCard.label)
                }
            }

            binding.messageHolder.threadSelectSimNumber.setTextColor(
                getProperTextColor().getContrastColor()
            )
            try {
                @SuppressLint("SetTextI18n")
                binding.messageHolder.threadSelectSimNumber.text =
                    (availableSIMCards[currentSIMCardIndex].id).toString()
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getProperSimIndex(
        availableSIMs: MutableList<SubscriptionInfo>,
        numbers: List<String>,
    ): Int {
        val userPreferredSimId = config.getUseSIMIdAtNumber(numbers.first())
        val userPreferredSimIdx =
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == userPreferredSimId }

        val lastMessage = messages.lastOrNull()
        val senderPreferredSimIdx = if (lastMessage?.isReceivedMessage() == true) {
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == lastMessage.subscriptionId }
        } else {
            null
        }

        val defaultSmsSubscriptionId = SmsManager.getDefaultSmsSubscriptionId()
        val systemPreferredSimIdx = if (defaultSmsSubscriptionId >= 0) {
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == defaultSmsSubscriptionId }
        } else {
            null
        }

        return userPreferredSimIdx ?: senderPreferredSimIdx ?: systemPreferredSimIdx ?: 0
    }

    private fun tryBlocking() {
        if (isOrWasThankYouInstalled()) {
            blockNumber()
        } else {
            FeatureLockedDialog(this) { }
        }
    }

    private fun blockNumber() {
        val numbers = participants.getAddresses()
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(
            resources.getString(org.fossify.commons.R.string.block_confirmation),
            numbersString
        )

        ConfirmationDialog(this, question) {
            ensureBackgroundThread {
                numbers.forEach {
                    addBlockedNumber(it)
                }
                refreshConversations()
                finish()
            }
        }
    }

    private fun askConfirmDelete() {
        val confirmationMessage = R.string.delete_whole_conversation_confirmation
        ConfirmationDialog(this, getString(confirmationMessage)) {
            ensureBackgroundThread {
                if (isRecycleBin) {
                    emptyMessagesRecycleBinForConversation(threadId)
                } else {
                    deleteConversation(threadId)
                }
                runOnUiThread {
                    refreshConversations()
                    finish()
                }
            }
        }
    }

    private fun askConfirmRestoreAll() {
        ConfirmationDialog(this, getString(R.string.restore_confirmation)) {
            ensureBackgroundThread {
                restoreAllMessagesFromRecycleBinForConversation(threadId)
                runOnUiThread {
                    refreshConversations()
                    finish()
                }
            }
        }
    }

    private fun archiveConversation() {
        ensureBackgroundThread {
            updateConversationArchivedStatus(threadId, true)
            runOnUiThread {
                refreshConversations()
                finish()
            }
        }
    }

    private fun unarchiveConversation() {
        ensureBackgroundThread {
            updateConversationArchivedStatus(threadId, false)
            runOnUiThread {
                refreshConversations()
                finish()
            }
        }
    }

    private fun dialNumber() {
        val phoneNumber = participants.first().phoneNumbers.first().normalizedNumber
        dialNumber(phoneNumber)
    }

    private fun copyNumberToClipboard() {
        val phoneNumber = conversation?.phoneNumber
            ?.ifEmpty { participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value }
            ?: return
        copyToClipboard(phoneNumber)
    }

    private fun managePeople() {
        if (binding.threadAddContacts.isVisible()) {
            hideKeyboard()
            binding.threadAddContacts.beGone()
        } else {
            showSelectedContacts()
            binding.threadAddContacts.beVisible()
            binding.addContactOrNumber.requestFocus()
            showKeyboard(binding.addContactOrNumber)
        }
    }

    private fun showSelectedContacts() {
        val properPrimaryColor = getProperPrimaryColor()

        val views = ArrayList<View>()
        participants.forEach { contact ->
            ItemSelectedContactBinding.inflate(layoutInflater).apply {
                val selectedContactBg =
                    AppCompatResources.getDrawable(
                        this@ThreadActivity,
                        R.drawable.item_selected_contact_background
                    )
                (selectedContactBg as LayerDrawable).findDrawableByLayerId(R.id.selected_contact_bg)
                    .applyColorFilter(properPrimaryColor)
                selectedContactHolder.background = selectedContactBg

                selectedContactName.text = contact.name
                selectedContactName.setTextColor(properPrimaryColor.getContrastColor())
                selectedContactRemove.applyColorFilter(properPrimaryColor.getContrastColor())

                selectedContactRemove.setOnClickListener {
                    if (contact.rawId != participants.first().rawId) {
                        removeSelectedContact(contact.rawId)
                    }
                }
                views.add(root)
            }
        }
        showSelectedContact(views)
    }

    private fun addSelectedContact(contact: SimpleContact) {
        binding.addContactOrNumber.setText("")
        if (participants.map { it.rawId }.contains(contact.rawId)) {
            return
        }

        participants.add(contact)
        showSelectedContacts()
        updateMessageType()
    }

    private fun markAsUnread() {
        ensureBackgroundThread {
            conversationsDB.markUnread(threadId)
            markThreadMessagesUnread(threadId)
            runOnUiThread {
                finish()
                bus?.post(Events.RefreshConversations())
            }
        }
    }

    private fun addNumberToContact(number: String? = null) {
        val phoneNumber = number
            ?: participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber
            ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            launchActivityIntent(this)
        }
    }

    private fun renameConversation() {
        RenameConversationDialog(this, conversation!!) { title ->
            ensureBackgroundThread {
                conversation = renameConversation(conversation!!, newTitle = title)
                runOnUiThread {
                    setupThreadTitle()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getThreadItems(): ArrayList<ThreadItem> {
        val items = ArrayList<ThreadItem>()
        if (isFinishing) {
            return items
        }

        messages.sortBy { it.date }

        var hadUnreadItems = false
        val cnt = messages.size
        for (i in 0 until cnt) {
            val message = messages.getOrNull(i) ?: continue
            items.add(message)

            if (message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
                items.add(ThreadSending(message.id))
            }

            if (!message.read) {
                hadUnreadItems = true
                markMessageRead(message.id, message.isMMS)
                conversationsDB.markRead(threadId)
            }
        }

        if (hadUnreadItems) {
            bus?.post(Events.RefreshConversations())
        }

        return items
    }

    private fun checkSendMessageAvailability() {
        binding.messageHolder.apply {
            if (threadTypeMessage.text!!.isNotEmpty() || pendingAttachmentUris.isNotEmpty()) {
                threadSendMessage.isEnabled = true
                threadSendMessage.isClickable = true
                threadSendMessage.alpha = 1f
            } else {
                threadSendMessage.isEnabled = false
                threadSendMessage.isClickable = false
                // keep the disabled state clearly readable for old eyes
                threadSendMessage.alpha = 0.8f
            }
        }

        updateMessageType()
    }

    private fun sendMessage() {
        var text = binding.messageHolder.threadTypeMessage.value
        if (text.isEmpty() && pendingAttachmentUris.isEmpty()) {
            showErrorToast(getString(org.fossify.commons.R.string.unknown_error_occurred))
            return
        }
        scrollToBottom()

        text = removeDiacriticsIfNeeded(text)

        val subscriptionId = availableSIMCards.getOrNull(currentSIMCardIndex)?.subscriptionId
            ?: SmsManager.getDefaultSmsSubscriptionId()

        sendNormalMessage(text, subscriptionId)
    }

    private fun sendNormalMessage(text: String, subscriptionId: Int) {
        val addresses = participants.getAddresses()
        val attachments = buildPendingAttachments(messageId = -1L)

        try {
            refreshedSinceSent = false
            sendMessageCompat(text, addresses, subscriptionId, attachments, messageToResend)
            ensureBackgroundThread {
                val messages = getMessages(threadId, limit = maxOf(1, attachments.size))
                    .filterNotInByKey(messages) { it.getStableId() }
                for (message in messages) {
                    insertOrUpdateMessage(message)
                }
            }
            clearCurrentMessage()

        } catch (e: Exception) {
            showErrorToast(e)
        } catch (e: Error) {
            showErrorToast(
                e.localizedMessage ?: getString(org.fossify.commons.R.string.unknown_error_occurred)
            )
        }
    }

    private fun clearCurrentMessage() {
        binding.messageHolder.threadTypeMessage.setText("")
        clearPendingAttachments()
        checkSendMessageAvailability()
    }

    private fun insertOrUpdateMessage(message: Message) {
        if (messages.map { it.id }.contains(message.id)) {
            val messageToReplace = messages.find { it.id == message.id }
            messages[messages.indexOf(messageToReplace)] = message
        } else {
            messages.add(message)
        }

        val newItems = getThreadItems()
        runOnUiThread {
            getOrCreateThreadAdapter().updateMessages(newItems, newItems.lastIndex)
            if (!refreshedSinceSent) {
                refreshMessages()
            }
        }
        messagesDB.insertOrUpdate(message)
        if (shouldUnarchive()) {
            updateConversationArchivedStatus(message.threadId, false)
            refreshConversations()
        }
    }

    // show selected contacts, properly split to new lines when appropriate
    // based on https://stackoverflow.com/a/13505029/1967672
    private fun showSelectedContact(views: ArrayList<View>) {
        binding.selectedContacts.removeAllViews()
        var newLinearLayout = LinearLayout(this)
        newLinearLayout.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        newLinearLayout.orientation = LinearLayout.HORIZONTAL

        val sideMargin =
            (binding.selectedContacts.layoutParams as RelativeLayout.LayoutParams).leftMargin
        val mediumMargin = resources.getDimension(org.fossify.commons.R.dimen.medium_margin).toInt()
        val parentWidth = realScreenSize.x - sideMargin * 2
        val firstRowWidth =
            parentWidth - resources.getDimension(org.fossify.commons.R.dimen.normal_icon_size)
                .toInt() + sideMargin / 2
        var widthSoFar = 0
        var isFirstRow = true

        for (i in views.indices) {
            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.HORIZONTAL
            layout.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            layout.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            views[i].measure(0, 0)

            var params = LayoutParams(views[i].measuredWidth, LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, mediumMargin, 0)
            layout.addView(views[i], params)
            layout.measure(0, 0)
            widthSoFar += views[i].measuredWidth + mediumMargin

            val checkWidth = if (isFirstRow) firstRowWidth else parentWidth
            if (widthSoFar >= checkWidth) {
                isFirstRow = false
                binding.selectedContacts.addView(newLinearLayout)
                newLinearLayout = LinearLayout(this)
                newLinearLayout.layoutParams =
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                newLinearLayout.orientation = LinearLayout.HORIZONTAL
                params = LayoutParams(layout.measuredWidth, layout.measuredHeight)
                params.topMargin = mediumMargin
                newLinearLayout.addView(layout, params)
                widthSoFar = layout.measuredWidth
            } else {
                if (!isFirstRow) {
                    (layout.layoutParams as LayoutParams).topMargin = mediumMargin
                }
                newLinearLayout.addView(layout)
            }
        }
        binding.selectedContacts.addView(newLinearLayout)
    }

    private fun removeSelectedContact(id: Int) {
        participants =
            participants.filter { it.rawId != id }.toMutableList() as ArrayList<SimpleContact>
        showSelectedContacts()
        updateMessageType()
    }

    private fun getPhoneNumbersFromIntent(): ArrayList<String> {
        val numberFromIntent = intent.getStringExtra(THREAD_NUMBER)
        val numbers = ArrayList<String>()

        if (numberFromIntent != null) {
            if (numberFromIntent.startsWith('[') && numberFromIntent.endsWith(']')) {
                val type = object : TypeToken<List<String>>() {}.type
                numbers.addAll(Gson().fromJson(numberFromIntent, type))
            } else {
                numbers.add(numberFromIntent)
            }
        }
        return numbers
    }

    private fun fixParticipantNumbers(
        participants: ArrayList<SimpleContact>,
        properNumbers: ArrayList<String>,
    ): ArrayList<SimpleContact> {
        for (number in properNumbers) {
            for (participant in participants) {
                participant.phoneNumbers = participant.phoneNumbers.map {
                    val numberWithoutPlus = number.replace("+", "")
                    if (numberWithoutPlus == it.normalizedNumber.trim()) {
                        if (participant.name == it.normalizedNumber) {
                            participant.name = number
                        }
                        PhoneNumber(number, 0, "", number)
                    } else {
                        PhoneNumber(it.normalizedNumber, 0, "", it.normalizedNumber)
                    }
                } as ArrayList<PhoneNumber>
            }
        }

        return participants
    }


    @Subscribe(threadMode = ThreadMode.ASYNC)
    fun refreshMessages(@Suppress("unused") event: Events.RefreshMessages) {
        if (isRecycleBin) {
            return
        }

        refreshedSinceSent = true
        allMessagesFetched = false

        if (isActivityVisible) {
            notificationManager.cancel(threadId.hashCode())
        }

        val lastMaxId = messages.filterNot { it.isScheduled }.maxByOrNull { it.id }?.id ?: 0L
        val newThreadId = getThreadId(participants.getAddresses().toSet())
        val newMessages = getMessages(newThreadId, includeScheduledMessages = false)
        if (messages.isNotEmpty() && messages.all { it.isScheduled } && newMessages.isNotEmpty()) {
            // update scheduled messages with real thread id
            threadId = newThreadId
            updateScheduledMessagesThreadId(
                messages = messages.filter { it.threadId != threadId },
                newThreadId = threadId
            )
        }

        messages = newMessages.apply {
            val scheduledMessages = messagesDB.getScheduledThreadMessages(threadId)
                .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
            addAll(scheduledMessages)
            if (config.useRecycleBin) {
                val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId).toSet()
                removeAll(recycledMessages)
            }
        }

        messages.filter { !it.isScheduled && !it.isReceivedMessage() && it.id > lastMaxId }
            .forEach { latestMessage ->
                messagesDB.insertOrIgnore(latestMessage)
            }

        setupAdapter()
        runOnUiThread {
            setupSIMSelector()
        }
    }

    private fun updateMessageType() {
        // the button always reads SEND, the SMS/MMS distinction only confuses here
        binding.messageHolder.threadSendMessage.setText(R.string.send_button)
    }

    private fun maybeSetupRecycleBinView() {
        if (isRecycleBin) {
            binding.messageHolder.root.beGone()
        }
    }

    private fun getBottomBarColor() = if (isDynamicTheme()) {
        resources.getColor(org.fossify.commons.R.color.you_bottom_bar_color)
    } else {
        getBottomNavigationBackgroundColor()
    }

    fun setupMessagingEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(
            binding.messageHolder.threadTypeMessage
        ) { view, insets ->
            val type = WindowInsetsCompat.Type.ime()
            val isKeyboardVisible = insets.isVisible(type)
            if (isKeyboardVisible) {
                val keyboardHeight = insets.getInsets(type).bottom
                val bottomBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

                // check keyboard height just to be sure, 150 seems like a good middle ground between ime and navigation bar
                config.keyboardHeight = if (keyboardHeight > 150) {
                    keyboardHeight - bottomBarHeight
                } else {
                    getDefaultKeyboardHeight()
                }
            }

            insets
        }
    }

    companion object {
        private const val SCROLL_TO_BOTTOM_FAB_LIMIT = 20
        private const val PREFETCH_THRESHOLD = 45
    }
}
