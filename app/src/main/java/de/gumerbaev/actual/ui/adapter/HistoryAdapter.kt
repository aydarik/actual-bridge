package de.gumerbaev.actual.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import de.gumerbaev.actual.R
import de.gumerbaev.actual.databinding.ItemHistoryBinding
import de.gumerbaev.actual.databinding.ItemMonthHeaderBinding
import de.gumerbaev.actual.model.ActualTransaction
import de.gumerbaev.actual.model.TransactionRecord
import de.gumerbaev.actual.util.LocationHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class HistoryAdapter(
    records: MutableList<TransactionRecord>,
    private val onResend: (TransactionRecord) -> Unit,
    private val onLocationClick: ((lat: Double, lng: Double) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val title: String, val spend: Double) : Row()
        data class Item(val record: TransactionRecord) : Row()
    }

    private var rows: List<Row> = buildRows(records)

    class HistoryViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)
    class HeaderViewHolder(val binding: ItemMonthHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    private fun buildRows(records: List<TransactionRecord>): List<Row> {
        val monthFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())
        val result = mutableListOf<Row>()
        records.sortedByDescending { it.timestamp }
            .groupBy { monthFormat.format(Date(it.timestamp)) }
            .forEach { (title, group) ->
                val spend = group
                    .filter { it.status != TransactionRecord.STATUS_DISCARDED }
                    .map { it.transaction }
                    .filter { it.type != ActualTransaction.TYPE_DEPOSIT }
                    .sumOf { abs(it.amount ?: 0.0) }
                result.add(Row.Header(title.replaceFirstChar { it.uppercase() }, spend))
                group.forEach { result.add(Row.Item(it)) }
            }
        return result
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemMonthHeaderBinding.inflate(inflater, parent, false))
        } else {
            HistoryViewHolder(ItemHistoryBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> {
                val header = (holder as HeaderViewHolder).binding
                header.tvMonthTitle.text = row.title
                header.tvMonthSpend.text = header.root.context.getString(R.string.month_spent, "%.2f".format(Locale.US, row.spend))
            }
            is Row.Item -> bindItem((holder as HistoryViewHolder).binding, row.record)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindItem(binding: ItemHistoryBinding, record: TransactionRecord) {
        val tx = record.transaction
        val context = binding.root.context

        with(binding) {
            tvHistoryPayee.text = tx.payee
            val lat = tx.latitude
            val lng = tx.longitude
            val hasLocation = lat != null && lng != null
            tvHistoryLocationIndicator.visibility = if (hasLocation) View.VISIBLE else View.GONE
            if (hasLocation) {
                tvHistoryLocationIndicator.setOnClickListener {
                    onLocationClick?.invoke(lat, lng) ?: LocationHelper.openInMaps(context, lat, lng)
                }
            } else {
                tvHistoryLocationIndicator.setOnClickListener(null)
            }

            val timeStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(record.timestamp))
            tvHistoryAccountDate.text = "${tx.account} • $timeStr"
            tvHistorySource.text = context.getString(R.string.history_from, record.sourcePackage)

            val isDeposit = tx.type == ActualTransaction.TYPE_DEPOSIT
            val amountFormatted = "%.2f".format(Locale.US, tx.amount?.let { abs(it) } ?: 0.0)

            if (isDeposit) {
                tvHistoryAmount.text = "+$amountFormatted"
                tvHistoryAmount.setTextColor(ContextCompat.getColor(context, R.color.secondary))
            } else {
                tvHistoryAmount.text = "-$amountFormatted"
                tvHistoryAmount.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }

            when (record.status) {
                TransactionRecord.STATUS_SENT -> {
                    tvHistoryStatus.text = context.getString(R.string.status_sent)
                    tvHistoryStatus.setBackgroundResource(R.drawable.bg_status_success)
                    tvHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.secondary))
                    btnHistoryResend.text = context.getString(R.string.btn_sent)
                    btnHistoryResend.isEnabled = false
                }
                TransactionRecord.STATUS_FAILED -> {
                    tvHistoryStatus.text = context.getString(R.string.status_failed)
                    tvHistoryStatus.setBackgroundResource(R.drawable.bg_status_error)
                    tvHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.danger))
                    btnHistoryResend.text = context.getString(R.string.btn_retry)
                    btnHistoryResend.isEnabled = true
                }
                TransactionRecord.STATUS_DISCARDED -> {
                    tvHistoryStatus.text = context.getString(R.string.status_discarded)
                    tvHistoryStatus.setBackgroundResource(R.drawable.bg_rounded_light)
                    tvHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                    btnHistoryResend.text = context.getString(R.string.btn_send)
                    btnHistoryResend.isEnabled = true
                }
                else -> {
                    tvHistoryStatus.text = context.getString(R.string.status_parsed)
                    tvHistoryStatus.setBackgroundResource(R.drawable.bg_rounded_light)
                    tvHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    btnHistoryResend.text = context.getString(R.string.btn_send)
                    btnHistoryResend.isEnabled = true
                }
            }

            btnHistoryResend.setOnClickListener {
                onResend(record)
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newRecords: List<TransactionRecord>) {
        rows = buildRows(newRecords)
        notifyDataSetChanged()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }
}
