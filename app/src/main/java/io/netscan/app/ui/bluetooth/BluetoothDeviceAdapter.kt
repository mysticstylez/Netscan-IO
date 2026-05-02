package io.netscan.app.ui.bluetooth

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.netscan.app.data.BluetoothDeviceInfo
import io.netscan.app.databinding.ItemBluetoothDeviceBinding

class BluetoothDeviceAdapter : ListAdapter<BluetoothDeviceInfo, BluetoothDeviceAdapter.BluetoothDeviceViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BluetoothDeviceViewHolder {
        val binding = ItemBluetoothDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BluetoothDeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BluetoothDeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class BluetoothDeviceViewHolder(
        private val binding: ItemBluetoothDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BluetoothDeviceInfo) {
            binding.deviceName.text = item.displayName
            binding.deviceAddress.text = item.address
            binding.deviceMeta.text = buildString {
                append(item.bondStateLabel)
                append(" • ")
                append(item.typeLabel)
                item.classLabel?.let {
                    append(" • ")
                    append(it)
                }
            }
            binding.deviceSignal.text = item.rssi?.let { "RSSI ${it} dBm" } ?: "RSSI unavailable"
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BluetoothDeviceInfo>() {
        override fun areItemsTheSame(oldItem: BluetoothDeviceInfo, newItem: BluetoothDeviceInfo): Boolean = oldItem.address == newItem.address
        override fun areContentsTheSame(oldItem: BluetoothDeviceInfo, newItem: BluetoothDeviceInfo): Boolean = oldItem == newItem
    }
}
