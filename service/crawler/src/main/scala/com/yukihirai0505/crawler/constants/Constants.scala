package com.yukihirai0505.crawler.constants

/**
  * author Yuki Hirai on 2017/08/30.
  */
object Constants {
  val instagramPostUrl = (shortcode: String) => s"https://www.instagram.com/p/$shortcode/"
  val instagramPostApiCount = 33
}
