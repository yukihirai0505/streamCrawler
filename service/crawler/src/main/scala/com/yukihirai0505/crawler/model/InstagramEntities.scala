package com.yukihirai0505.crawler.model

import com.yukihirai0505.iService.responses.PageInfo

case class InstagramHashTagEntity(
                                   tagName: String,
                                   mediaCount: Long = 0,
                                   isBan: Boolean = false,
                                   timestamp: String = ""
                                 )

case class InstagramMediaDataEntity(
                                     mediaId: String,
                                     userId: String,
                                     createdTime: String,
                                     link: String,
                                     tagName: Seq[String],
                                     likes: Long,
                                     comments: Long,
                                     caption: String,
                                     timestamp: String
                                   )

case class InstagramMediaDto(
                              hashTag: String,
                              instagramMedia: Seq[InstagramMediaDataEntity] = Seq.empty,
                              pageInfo: Option[PageInfo] = None
                            )

case class InstagramDto[T](
                            dto: T,
                            exception: Option[Exception] = None
                          )
