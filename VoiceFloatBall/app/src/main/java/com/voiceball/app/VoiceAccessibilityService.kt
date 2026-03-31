package com.voiceball.app

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：将语音识别结果填入当前焦点输入框
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        // 单例，供 VoiceRecognitionActivity 调用
        var instance: VoiceAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * 将文字插入当前焦点输入框
     * 优先使用 ACTION_SET_TEXT（API 21+），失败则回退到剪贴板粘贴
     */
    fun insertText(text: String) {
        val root = rootInActiveWindow ?: run {
            fallbackClipboard(text)
            return
        }

        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditableNode(root)

        if (focusedNode == null) {
            fallbackClipboard(text)
            return
        }

        // 方法1：直接设置文本
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)

        if (!success) {
            // 方法2：剪贴板粘贴
            setClipboard(text)
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        focusedNode.recycle()
    }

    /**
     * 递归查找第一个可编辑节点
     */
    private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstEditableNode(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun fallbackClipboard(text: String) {
        setClipboard(text)
        // 通知用户手动粘贴（Ctrl+V / 长按粘贴）
        android.widget.Toast.makeText(
            this,
            "已复制到剪贴板，请长按粘贴",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun setClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("voice_input", text))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要监听事件，只需要提供 insertText 能力
    }

    override fun onInterrupt() {}
}
