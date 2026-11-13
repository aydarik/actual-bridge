package de.gumerbaev.actual.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.gumerbaev.actual.R
import de.gumerbaev.actual.databinding.ItemRuleBinding
import de.gumerbaev.actual.model.ParsingRule

class RulesAdapter(
    private var rules: MutableList<ParsingRule>,
    private val onToggleEnabled: (ParsingRule, Boolean) -> Unit,
    private val onEdit: (ParsingRule) -> Unit,
    private val onDelete: (ParsingRule) -> Unit
) : RecyclerView.Adapter<RulesAdapter.RuleViewHolder>() {

    class RuleViewHolder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        val rule = rules[position]
        with(holder.binding) {
            tvRuleName.text = rule.name
            tvTargetPackage.text = root.context.getString(
                R.string.rule_app,
                if (rule.targetPackage.isBlank()) "*" else rule.targetPackage
            )
            tvRegexPreview.text = rule.textRegex
            val accountLabel = when {
                rule.account.isNotBlank() && rule.accountGroup.isNotBlank() -> "${rule.account} (?<${rule.accountGroup}>)"
                rule.account.isNotBlank() -> rule.account
                rule.accountGroup.isNotBlank() -> "?<${rule.accountGroup}>"
                else -> ""
            }
            tvAccountBadge.text = if (accountLabel.isNotBlank()) "$accountLabel • ${rule.defaultType}" else rule.defaultType

            root.alpha = if (rule.enabled) 1.0f else 0.65f

            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = rule.enabled
            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                rule.enabled = isChecked
                root.animate().alpha(if (isChecked) 1.0f else 0.65f).setDuration(150).start()
                onToggleEnabled(rule, isChecked)
            }

            root.setOnClickListener { onEdit(rule) }
            btnEditRule.setOnClickListener { onEdit(rule) }
            btnDeleteRule.setOnClickListener { onDelete(rule) }
        }
    }

    override fun getItemCount(): Int = rules.size

    fun updateData(newRules: List<ParsingRule>) {
        val oldList = rules.map { it.copy() }
        val newList = newRules.map { it.copy() }
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition].id == newList[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition] == newList[newItemPosition]
            }
        })
        rules.clear()
        rules.addAll(newRules)
        diffResult.dispatchUpdatesTo(this)
    }
}
