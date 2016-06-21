package com.org.hb

import android.accessibilityservice.AccessibilityService
import android.app.Instrumentation
import android.app.Notification
import android.app.PendingIntent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*
import java.util.regex.Pattern

/**
 * Created by huchen on 2016/3/3 0003.
 */
class HBService : AccessibilityService() {

    /**
     * 已获取的红包队列
     */
    private val fetchedIdentifiers = ArrayList<String>()
    /**
     * 待抢的红包队列
     */
    private val nodesToFetch = ArrayList<AccessibilityNodeInfo>()

    override fun onAccessibilityEvent(p0: AccessibilityEvent) {
        var event: Int = p0.eventType
        when (event) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                var tests: List<CharSequence> = p0.text;
                if (tests != null) {
                    for (test in tests) {
                        var content: String = test.toString()
                        if (content.contains("[微信红包]")) {
                            if (p0.parcelableData != null && p0.parcelableData is Notification) {
                                var notification: Notification = p0.parcelableData as Notification
                                var pendingIntent: PendingIntent = notification.contentIntent
                                pendingIntent.send()
                            }
                        }
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                var className: String = p0.className.toString();
                if (className.equals("com.tencent.mm.ui.LauncherUI")) {
                    //开始抢红包
                    getPacket()
                } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI")) {
                    //开始打开红包
                    openPacket()
                } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI")) {
                    Log.e("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI", "")
                    //                    closePacket();
                }
            }
        }
    }

    fun closePacket() {
        var handler: Thread = Thread()
        var runnable: Runnable = Runnable {
            run {
                var inst: Instrumentation = Instrumentation();
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            }
        }
        Thread(runnable).start();
    }

    fun openPacket() {
        var nodeInfo: AccessibilityNodeInfo
        try {
            nodeInfo = getRootInActiveWindow();
            if (nodeInfo != null) {
                val list: List<AccessibilityNodeInfo> = nodeInfo
                        .findAccessibilityNodeInfosByViewId("com.tencent.mm:id/b43")
                for (n in list) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        } catch (e: NullPointerException) {
            openPacket()
        }
    }

    fun getPacket() {
        var rootNode: AccessibilityNodeInfo = getRootInActiveWindow()
        var name: String = ""
        val list: List<AccessibilityNodeInfo> = rootNode
                .findAccessibilityNodeInfosByViewId("com.tencent.mm:id/ces")
        for (n in list){
            name = n.text.toString()
        }
        recycle(rootNode, name)
    }

    fun recycle(info: AccessibilityNodeInfo, name: String) {
        Log.e("com.tencent.mm:id/ces 获取name", name)
        if (info.childCount == 0) {

            if (info.text != null) {
                if ("领取红包".equals(info.text.toString())) {
                    //这里有一个问题需要注意，就是需要找到一个可以点击的View
                    info.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                    var str: String = info.toString().substring(0, 57) + name

                    Log.e("AccessibilityNodeInfo 所有信息", info.toString())
                    Log.e("AccessibilityNodeInfo 内存池地址", str)

                    if (!fetchedIdentifiers.contains(str)) {
                        var parent: AccessibilityNodeInfo = info.parent;
                        while (parent != null) {
                            Log.i("demo", "parent isClick:" + parent.isClickable);

                            if (parent.isClickable) {
                                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                break;
                            }
                            parent = parent.parent;
                        }
                        fetchedIdentifiers.add(str)
                    }

                    Log.e("fetchedIdentifiers 集合中的数据", str)

                }
            }

        } else {
            try {
                for (i in 0..info.childCount) {
                    if (info.getChild(i) != null) {
                        recycle(info.getChild(i), "");
                    }
                }
            } catch (e: IndexOutOfBoundsException) {

            }
        }
    }

    /**
     * 将节点对象的id和红包上的内容合并
     * 用于表示一个唯一的红包

     * @param node 任意对象
     * *
     * @return 红包标识字符串
     */
    private fun getHongbaoHash(node: AccessibilityNodeInfo): String? {
        /* 获取红包上的文本 */
        val content: String
        try {
            val i = node.parent.getChild(0)
            content = i.text.toString()
        } catch (npr: NullPointerException) {
            return null
        }

        return content + "@" + getNodeId(node)
    }

    /**
     * 获取节点对象唯一的id，通过正则表达式匹配
     * AccessibilityNodeInfo@后的十六进制数字

     * @param node AccessibilityNodeInfo对象
     * *
     * @return id字符串
     */
    private fun getNodeId(node: AccessibilityNodeInfo): String {
        /* 用正则表达式匹配节点Object */
        val objHashPattern = Pattern.compile("(?<=@)[0-9|a-z]+(?=;)")
        val objHashMatcher = objHashPattern.matcher(node.toString())

        // AccessibilityNodeInfo必然有且只有一次匹配，因此不再作判断
        objHashMatcher.find()

        return objHashMatcher.group(0)
    }

    override fun onInterrupt() {
    }

}