package de.gumerbaev.actual.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
            tvTargetPackage.text = "App: ${if (rule.targetPackage.isBlank()) "*" else rule.targetPackage}"
            tvRegexPreview.text = rule.textRegex
            tvAccountBadge.text = if (rule.account.isNotBlank()) "${rule.account} • ${rule.defaultType}" else rule.defaultType

            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = rule.enabled
            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                rule.enabled = isChecked
                onToggleEnabled(rule, isChecked)
            }

            btnEditRule.setOnClickListener { onEdit(rule) }
            btnDeleteRule.setOnClickListener { onDelete(rule) }
        }
    }

    override fun getItemCount(): Int = rules.size

    fun updateData(newRules: List<ParsingRule>) {
        rules.clear()
        rules.addAll(newRules)
        notifyDataSetChanged()
    }
}
