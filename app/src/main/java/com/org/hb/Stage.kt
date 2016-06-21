package com.org.hb

/**
 * Created by ZhongyiTong on 9/30/15.
 *
 *
 * 记录抢红包时对应的阶段
 */
class Stage
/**
 * 单例设计，防止通过构造函数创建对象
 */
private constructor() {

    /**
     * 当前阶段
     */
    private var currentStage = FETCHED_STAGE

    /**
     * 阶段互斥，不允许多次回调进入同一阶段
     */
    var mutex = false

    /**
     * 记录接下来的阶段

     * @param _stage
     */
    fun entering(_stage: Int) {
        stageInstance!!.currentStage = _stage
        mutex = false
    }

    /**
     * 记录当前的阶段
     */
    fun getCurrentStage(): Int {
        return stageInstance!!.currentStage
    }

    companion object {
        /**
         * 单例设计
         */
        private var stageInstance: Stage? = null

        /**
         * 阶段常量
         */
        val FETCHING_STAGE = 0
        val OPENING_STAGE = 1
        val FETCHED_STAGE = 2
        val OPENED_STAGE = 3

        /**
         * 单例设计，惰性实例化

         * @return 返回唯一的实例
         */
        val instance: Stage
            get() {
                if (stageInstance == null) {
                    stageInstance = Stage()
                }
                return stageInstance!!
            }
    }
}
