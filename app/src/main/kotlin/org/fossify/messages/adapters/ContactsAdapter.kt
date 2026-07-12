package org.fossify.messages.adapters

import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.databinding.ItemContactWithNumberBinding
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.SimpleContact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.activities.SimpleActivity

class ContactsAdapter(
    activity: SimpleActivity,
    var contacts: ArrayList<SimpleContact>,
    recyclerView: MyRecyclerView,
    private val showNumbers: Boolean = true,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    private var fontSize = activity.getTextSize()

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contacts.getOrNull(position)?.rawId

    override fun getItemKeyPosition(key: Int) = contacts.indexOfFirst { it.rawId == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactWithNumberBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, allowSingleClick = true, allowLongClick = false) { itemView, _ ->
            setupView(itemView, contact)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: ArrayList<SimpleContact>) {
        val oldHashCode = contacts.hashCode()
        val newHashCode = newContacts.hashCode()
        if (newHashCode != oldHashCode) {
            contacts = newContacts
            notifyDataSetChanged()
        }
    }

    private fun setupView(view: View, contact: SimpleContact) {
        ItemContactWithNumberBinding.bind(view).apply {
            val verticalPadding = activity.resources.getDimensionPixelSize(
                if (showNumbers) org.fossify.commons.R.dimen.bigger_margin else org.fossify.commons.R.dimen.normal_margin
            )
            itemContactFrame.setPadding(
                itemContactFrame.paddingLeft, verticalPadding, itemContactFrame.paddingRight, verticalPadding
            )

            itemContactName.apply {
                text = contact.name
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.27f)
            }

            itemContactNumber.apply {
                beVisibleIf(showNumbers)
                if (showNumbers) {
                    text = TextUtils.join(", ", contact.phoneNumbers.map { it.normalizedNumber })
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.95f)
                }
            }

            val iconSize = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.list_icon_size_large)
            itemContactImage.updateLayoutParams<ViewGroup.LayoutParams> {
                width = iconSize
                height = iconSize
            }

            if (!showNumbers) {
                // with the number row gone, center the name and avatar vertically
                itemContactName.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomToTop = ConstraintLayout.LayoutParams.UNSET
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
                itemContactImage.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
            }

            SimpleContactsHelper(activity).loadContactImage(contact.photoUri, itemContactImage, contact.name)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = ItemContactWithNumberBinding.bind(holder.itemView)
            Glide.with(activity).clear(binding.itemContactImage)
        }
    }
}
