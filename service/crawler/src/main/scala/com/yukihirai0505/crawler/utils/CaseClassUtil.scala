package com.yukihirai0505.crawler.utils

/**
  * author Yuki Hirai on 2017/08/31.
  */
object CaseClassUtil {

  /**
    * case class を Map[String, Any] へ変換します
    *
    * @param cc
    * @return
    */
  def getCCParams(cc: AnyRef): Map[String, Any] = {
    (Map[String, Any]() /: cc.getClass.getDeclaredFields) { (a, f) =>
      f.setAccessible(true)
      a + (f.getName -> f.get(cc))
    }
  }
}