package org.fossify.messages.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColorStateList
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.maybeShowNumberPickerDialog
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.R
import org.fossify.messages.adapters.ContactsAdapter
import org.fossify.messages.databinding.ActivityNewConversationBinding
import org.fossify.messages.databinding.ItemRecipientChipBinding
import org.fossify.messages.extensions.addDividerIfNeeded
import org.fossify.messages.extensions.getThreadId
import org.fossify.messages.helpers.SmsIntentParser
import org.fossify.messages.helpers.THREAD_ATTACHMENT_URI
import org.fossify.messages.helpers.THREAD_ATTACHMENT_URIS
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_NUMBER
import org.fossify.messages.helpers.THREAD_TEXT
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.messaging.isShortCodeWithLetters
import java.net.URLDecoder
import java.util.Locale

class NewConversationActivity : SimpleActivity() {
    private val PICKED_RECIPIENTS = "picked_recipients"

    private var allContacts = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()

    // number -> displayed name, in the order the user picked them
    private val selectedRecipients = LinkedHashMap<String, String>()

    private val binding by viewBinding(ActivityNewConversationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(R.string.new_conversation)
        updateTextColors(binding.newConversationHolder)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.contactsList))
        setupMaterialScrollListener(
            scrollingView = binding.contactsList,
            topAppBar = binding.newConversationAppbar
        )

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.newConversationAddress.requestFocus()

        // the To: section lines carry meaning, make them a notch stronger than the stock divider
        val strongDividerColor = getProperTextColor().adjustAlpha(0.4f)
        binding.toSectionTopDivider.setBackgroundColor(strongDividerColor)
        binding.numberSuggestionDivider.setBackgroundColor(strongDividerColor)
        binding.addContactDivider.setBackgroundColor(strongDividerColor)
        binding.newConversationAddressUnderline.setBackgroundColor(strongDividerColor)

        // picked recipients survive activity recreation
        savedInstanceState?.getString(PICKED_RECIPIENTS)?.let { json ->
            val type = object : TypeToken<LinkedHashMap<String, String>>() {}.type
            selectedRecipients.putAll(Gson().fromJson(json, type))
            refreshRecipientChips()
        }

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PICKED_RECIPIENTS, Gson().toJson(selectedRecipients))
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.newConversationAppbar, NavigationIcon.None)
        binding.noContactsPlaceholder2.setTextColor(getProperPrimaryColor())
        binding.noContactsPlaceholder2.underlineText()

        val properPrimaryColor = getProperPrimaryColor()
        binding.newConversationCreate.apply {
            background = AppCompatResources.getDrawable(
                this@NewConversationActivity, R.drawable.button_rounded_background
            )
            background.applyColorFilter(properPrimaryColor)
            setOnClickListener { createConversation() }
        }
        binding.newConversationCreateText.setTextColor(properPrimaryColor.getContrastColor())
        updateCreateButtonState()
    }

    private fun initContacts() {
        if (isThirdPartyIntent()) {
            return
        }

        fetchContacts()
        binding.newConversationAddress.onTextChangeListener { searchString ->
            // typed numbers get a tappable suggestion row below the To: field,
            // framed by a separator line above and below it
            val looksLikeNumber = searchString.length > 2 && searchString.none { it.isLetter() }
            binding.numberSuggestion.beVisibleIf(looksLikeNumber)
            binding.numberSuggestionDivider.beVisibleIf(looksLikeNumber)
            binding.numberSuggestionText.text = searchString

            filterContactsAndSetup(searchString)
        }

        // suggestion row mirrors the contact rows: primary circle with a white plus
        val suggestionPrimaryColor = getProperPrimaryColor()
        binding.numberSuggestionIcon.background.applyColorFilter(suggestionPrimaryColor)
        binding.numberSuggestionIcon.applyColorFilter(suggestionPrimaryColor.getContrastColor())
        binding.numberSuggestionText.setTextSize(
            TypedValue.COMPLEX_UNIT_PX, getTextSize() * 1.27f
        )
        binding.numberSuggestion.setOnClickListener {
            addTypedNumber()
        }

        binding.newConversationAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTypedNumber()
                true
            } else {
                false
            }
        }

        binding.noContactsPlaceholder2.setOnClickListener {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    fetchContacts()
                }
            }
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.contactsLetterFastscroller.textColor = getProperTextColor().getColorStateList()
        binding.contactsLetterFastscroller.pressedTextColor = properPrimaryColor
        binding.contactsLetterFastscrollerThumb.setupWithFastScroller(binding.contactsLetterFastscroller)
        binding.contactsLetterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
        binding.contactsLetterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
    }

    private fun isThirdPartyIntent(): Boolean {
        val result = SmsIntentParser.parse(intent)

        if (result != null && (result.first.isNotEmpty() || result.second.isNotEmpty())) {
            val (body, recipients) = result
            launchThreadActivity(
                phoneNumber = URLDecoder.decode(recipients.replace("+", "%2b").trim()),
                name = "",
                body = body
            )
            finish()
            return true
        }
        return false
    }

    private fun fetchContacts() {
        val privateCursor = getMyContactsCursor(false, true)
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            SimpleContactsHelper(this).getAvailableContacts(false) {
                allContacts = it

                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }

                runOnUiThread {
                    filterContactsAndSetup(binding.newConversationAddress.value)
                }
            }
        }
    }

    // picked contacts disappear from the list and come back when their chip is removed
    private fun filterContactsAndSetup(searchString: String) {
        val filteredContacts = ArrayList<SimpleContact>()
        allContacts.forEach { contact ->
            val isAlreadyPicked = contact.phoneNumbers.any {
                selectedRecipients.containsKey(it.normalizedNumber)
            }
            if (isAlreadyPicked) {
                return@forEach
            }

            if (searchString.isEmpty() ||
                contact.phoneNumbers.any { it.normalizedNumber.contains(searchString, true) } ||
                contact.name.contains(searchString, true) ||
                contact.name.contains(searchString.normalizeString(), true) ||
                contact.name.normalizeString().contains(searchString, true)
            ) {
                filteredContacts.add(contact)
            }
        }

        filteredContacts.sortWith(compareBy { !it.name.startsWith(searchString, true) })
        setupAdapter(filteredContacts)
    }

    private fun addTypedNumber() {
        val number = binding.newConversationAddress.value
        if (number.isEmpty()) {
            return
        }

        if (isShortCodeWithLetters(number)) {
            binding.newConversationAddress.setText("")
            toast(R.string.invalid_short_code, length = Toast.LENGTH_LONG)
            return
        }

        addRecipient(number, number)
    }

    private fun addRecipient(number: String, name: String) {
        binding.newConversationAddress.setText("")
        if (!selectedRecipients.containsKey(number)) {
            selectedRecipients[number] = name
            refreshRecipientChips()
        }
    }

    private fun removeRecipient(number: String) {
        selectedRecipients.remove(number)
        refreshRecipientChips()
    }

    private fun refreshRecipientChips() {
        val chipHolder = binding.selectedRecipientsHolder
        // every child except the last one (the typing field) is a chip line
        while (chipHolder.childCount > 1) {
            chipHolder.removeViewAt(0)
        }

        val properPrimaryColor = getProperPrimaryColor()
        val contrastColor = properPrimaryColor.getContrastColor()
        var index = 0
        selectedRecipients.forEach { (number, name) ->
            val chipBinding = ItemRecipientChipBinding.inflate(layoutInflater)
            chipBinding.apply {
                root.background.applyColorFilter(properPrimaryColor)
                recipientChipName.text = name
                recipientChipName.setTextColor(contrastColor)
                recipientChipRemove.applyColorFilter(contrastColor)
                // pressing anywhere on the chip removes it
                root.setOnClickListener {
                    removeRecipient(number)
                }
                recipientChipRemove.setOnClickListener {
                    removeRecipient(number)
                }
            }
            chipHolder.addView(chipBinding.root, index++)
        }

        // the placeholder is only needed while nothing is picked yet
        binding.newConversationAddress.hint = if (selectedRecipients.isEmpty()) {
            getString(R.string.add_contact_or_number)
        } else {
            ""
        }
        filterContactsAndSetup(binding.newConversationAddress.value)
        updateCreateButtonState()
    }

    private fun updateCreateButtonState() {
        val enabled = selectedRecipients.isNotEmpty()
        binding.newConversationCreate.isEnabled = enabled
        // keep the disabled state clearly readable for old eyes
        binding.newConversationCreate.alpha = if (enabled) 1f else 0.8f
    }

    private fun createConversation() {
        if (selectedRecipients.isEmpty()) {
            return
        }

        val numbers = selectedRecipients.keys
        val name = if (numbers.size == 1) selectedRecipients.values.first() else ""
        launchThreadActivity(numbers.joinToString(";"), name)
    }

    private fun setupAdapter(contacts: ArrayList<SimpleContact>) {
        val hasContacts = contacts.isNotEmpty()
        binding.contactsList.beVisibleIf(hasContacts)
        // no line needed when there is nothing around it to separate
        binding.addContactDivider.beVisibleIf(hasContacts || binding.numberSuggestion.isVisible())
        // the "No contacts found" text is intentionally never shown, it doesn't help here
        binding.noContactsPlaceholder.beGone()
        binding.noContactsPlaceholder2.beGone()

        val currAdapter = binding.contactsList.adapter
        if (currAdapter == null) {
            ContactsAdapter(this, contacts, binding.contactsList, showNumbers = false) {
                val contact = it as SimpleContact
                maybeShowNumberPickerDialog(contact.phoneNumbers) { number ->
                    addRecipient(number.normalizedNumber, contact.name)
                }
            }.apply {
                binding.contactsList.adapter = this
            }
            binding.contactsList.addDividerIfNeeded()

            if (areSystemAnimationsEnabled) {
                binding.contactsList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).updateContacts(contacts)
        }

        setupLetterFastscroller(contacts)
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        binding.contactsLetterFastscroller.setupWithRecyclerView(binding.contactsList, { position ->
            try {
                val name = contacts[position].name
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(
                    character.uppercase(Locale.getDefault()).normalizeString()
                )
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    private fun launchThreadActivity(phoneNumber: String, name: String, body: String = "") {
        hideKeyboard()
        val numbers = phoneNumber.split(";").toSet()
        val number = if (numbers.size == 1) phoneNumber else Gson().toJson(numbers)
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, getThreadId(numbers))
            putExtra(THREAD_TITLE, name)
            putExtra(THREAD_TEXT, body.ifEmpty { intent.getStringExtra(Intent.EXTRA_TEXT) })
            putExtra(THREAD_NUMBER, number)

            if (intent.action == Intent.ACTION_SEND && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URI, uri?.toString())
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.extras?.containsKey(
                    Intent.EXTRA_STREAM
                ) == true
            ) {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URIS, uris)
            }

            startActivity(this)
        }
    }
}
