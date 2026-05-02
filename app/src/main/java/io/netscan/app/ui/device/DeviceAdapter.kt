package io.netscan.app.ui.device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.netscan.app.data.DeviceInfo
import io.netscan.app.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onOpenDetails: (DeviceInfo) -> Unit,
    private val onOpenSsh: (DeviceInfo) -> Unit,
    private val onOpenTelnet: (DeviceInfo) -> Unit,
    private val onOpenSftp: (DeviceInfo) -> Unit,
    private val onOpenPacketTool: (DeviceInfo) -> Unit
) : ListAdapter<DeviceInfo, DeviceAdapter.DeviceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding, onOpenDetails, onOpenSsh, onOpenTelnet, onOpenSftp, onOpenPacketTool)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceBinding,
        private val onOpenDetails: (DeviceInfo) -> Unit,
        private val onOpenSsh: (DeviceInfo) -> Unit,
        private val onOpenTelnet: (DeviceInfo) -> Unit,
        private val onOpenSftp: (DeviceInfo) -> Unit,
        private val onOpenPacketTool: (DeviceInfo) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DeviceInfo) {
            binding.deviceTitle.text = item.displayName
            binding.deviceSubtitle.text = item.ipAddress
            binding.deviceStatus.text = if (item.openPorts.isEmpty()) "Responding host" else "Interactive services found"
            binding.devicePorts.text = if (item.openPorts.isEmpty()) {
                "Ports: none of the common probes responded"
            } else {
                "Ports: ${item.portSummaries.joinToString("  •  ")}"
            }
            binding.deviceServices.text = if (item.services.isEmpty()) {
                "Hostname: ${item.hostName ?: "not announced"}"
            } else {
                "Hostname: ${item.hostName ?: "not announced"}"
            }
            binding.detailButton.setOnClickListener { onOpenDetails(item) }
            binding.sshButton.isEnabled = 22 in item.openPorts
            binding.sshButton.alpha = if (22 in item.openPorts) 1f else 0.45f
            binding.sshButton.setOnClickListener { onOpenSsh(item) }
            binding.telnetButton.isEnabled = 23 in item.openPorts
            binding.telnetButton.alpha = if (23 in item.openPorts) 1f else 0.45f
            binding.telnetButton.setOnClickListener { onOpenTelnet(item) }
            binding.sftpButton.isEnabled = 22 in item.openPorts
            binding.sftpButton.alpha = if (22 in item.openPorts) 1f else 0.45f
            binding.sftpButton.setOnClickListener { onOpenSftp(item) }
            binding.packetButton.setOnClickListener { onOpenPacketTool(item) }
            binding.root.setOnClickListener { onOpenDetails(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DeviceInfo>() {
        override fun areItemsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem.ipAddress == newItem.ipAddress
        }

        override fun areContentsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
            return oldItem == newItem
        }
    }
}
