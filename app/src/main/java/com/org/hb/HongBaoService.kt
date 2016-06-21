package com.org.hb

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*
import java.util.regex.Pattern

/**
 * Created by ZhongyiTong on 9/30/15.
 *
 *
 * 抢红包主要的逻辑部分
 */
class HongbaoService : AccessibilityService() {
    /**
     * 已获取的红包队列
     */
    private val fetchedIdentifiers = ArrayList<String>()
    /**
     * 待抢的红包队列
     */
    private val nodesToFetch = ArrayList<AccessibilityNodeInfo>()
    /**
     * 尝试次数
     */
    private var ttl = 0

    /**
     * AccessibilityEvent的回调方法
     *
     *
     * 当窗体状态或内容变化时，根据当前阶段选择相应的入口

     * @param event 事件
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    override fun onAccessibilityEvent(event: AccessibilityEvent) {

        if (Stage.instance.mutex) return

        Stage.instance.mutex = true

        try {
            handleWindowChange(event.source)
        } catch (e: IllegalStateException) {
            Log.e("IllegalStateException: ", e.message)
            e.printStackTrace()
        } finally {
            Stage.instance.mutex = false
        }

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun handleWindowChange(nodeInfo: AccessibilityNodeInfo) {
        when (Stage.instance.getCurrentStage()) {
            Stage.OPENING_STAGE -> {
                // 调试信息，打印TTL
                // Log.d("TTL", String.valueOf(ttl));

                /* 如果打开红包失败且还没到达最大尝试次数，重试 */
                if (openHongbao(nodeInfo) == -1 && ttl < MAX_TTL) return

                ttl = 0
                Stage.instance.entering(Stage.FETCHED_STAGE)
                performMyGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                if (nodesToFetch.size == 0) handleWindowChange(nodeInfo)
            }
            Stage.OPENED_STAGE -> {
                val successNodes = nodeInfo.findAccessibilityNodeInfosByText("红包详情")
                if (successNodes.isEmpty() && ttl < MAX_TTL) {
                    ttl += 1
                    return
                }
                ttl = 0
                Stage.instance.entering(Stage.FETCHED_STAGE)
                performMyGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
            Stage.FETCHED_STAGE -> {
                /* 先消灭待抢红包队列中的红包 */
                if (nodesToFetch.size > 0) {
                    /* 从最下面的红包开始戳 */
                    val node = nodesToFetch.removeAt(nodesToFetch.size - 1)
                    if (node.parent != null) {
                        val id = getHongbaoHash(node) ?: return

                        fetchedIdentifiers.add(id)

                        // 调试信息，在每次打开红包后打印出已经获取的红包
                        // Log.d("fetched", Arrays.toString(fetchedIdentifiers.toArray()));

                        Stage.instance.entering(Stage.OPENING_STAGE)
                        node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    return
                }

                Stage.instance.entering(Stage.FETCHING_STAGE)
                fetchHongbao(nodeInfo)
                Stage.instance.entering(Stage.FETCHED_STAGE)
            }
        }
    }


    /**
     * 如果已经接收到红包并且还没有戳开
     *
     *
     * 在聊天页面中，查找包含“领取红包”的节点，
     * 将这些节点去重后加入待抢红包队列

     * @param nodeInfo 当前窗体的节点信息
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun fetchHongbao(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return

        /* 聊天会话窗口，遍历节点匹配“领取红包” */
        val fetchNodes = nodeInfo.findAccessibilityNodeInfosByText("领取红包")

        if (fetchNodes.isEmpty()) return

        for (cellNode in fetchNodes) {
            val id = getHongbaoHash(cellNode)

            /* 如果节点没有被回收且该红包没有抢过 */
            if (id != null && !fetchedIdentifiers.contains(id)) {
                nodesToFetch.add(cellNode)
            }
        }

        // 调试信息，在每次fetch后打印出待抢红包
        // Log.d("toFetch", Arrays.toString(nodesToFetch.toArray()));
    }


    /**
     * 如果戳开红包但还未领取
     *
     *
     * 第一种情况，当界面上出现“过期”(红包超过有效时间)、
     * “手慢了”(红包发完但没抢到)或“红包详情”(已经抢到)时，
     * 直接返回聊天界面
     *
     *
     * 第二种情况，界面上出现“拆红包”时
     * 点击该节点，并将阶段标记为OPENED_STAGE
     *
     *
     * 第三种情况，以上节点没有出现，
     * 说明窗体可能还在加载中，维持当前状态，TTL增加，返回重试

     * @param nodeInfo 当前窗体的节点信息
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun openHongbao(nodeInfo: AccessibilityNodeInfo?): Int {
        if (nodeInfo == null) return -1

        /* 戳开红包，红包已被抢完，遍历节点匹配“红包详情”、“手慢了”和“过期” */
        val failureNoticeNodes = ArrayList<AccessibilityNodeInfo>()
        failureNoticeNodes.addAll(nodeInfo.findAccessibilityNodeInfosByText("红包详情"))
        failureNoticeNodes.addAll(nodeInfo.findAccessibilityNodeInfosByText("手慢了"))
        failureNoticeNodes.addAll(nodeInfo.findAccessibilityNodeInfosByText("过期"))
        if (!failureNoticeNodes.isEmpty()) {
            return 0
        }

        /* 戳开红包，红包还没抢完，遍历节点匹配“拆红包” */
        val successNoticeNodes = nodeInfo.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/b43");
        val preventNoticeNodes = nodeInfo.findAccessibilityNodeInfosByText("领取红包")
        if (!successNoticeNodes.isEmpty()) {
            val openNode = successNoticeNodes[successNoticeNodes.size - 1]
            Stage.instance.entering(Stage.OPENED_STAGE)
            openNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return 0
        } else {
            Stage.instance.entering(Stage.OPENING_STAGE)
            ttl += 1
            return -1
        }
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

    override fun onInterrupt() {

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    fun performMyGlobalAction(action: Int) {
        Stage.instance.mutex = false
        performGlobalAction(action)
    }

    companion object {

        /**
         * 允许的最大尝试次数
         */
        private val MAX_TTL = 24
    }
}
