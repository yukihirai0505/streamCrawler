package com.yukihirai0505.crawler.service

import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.http.index.IndexResponse
import com.sksamuel.elastic4s.http.search.{SearchHit, SearchResponse}
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.LazyLogging
import com.yukihirai0505.crawler.model.{InstagramDto, InstagramHashTagEntity, InstagramMediaDataEntity, InstagramMediaDto}
import com.yukihirai0505.crawler.utils.{CaseClassUtil, Config, DateUtil}
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Elasticsearchサービス
  * author Yuki Hirai on 2017/10/10.
  */
object ElasticsearchService extends ElasticsearchConstants with Config with LazyLogging {


  private val host = elasticSearchConfig.getString("host")
  private val port = elasticSearchConfig.getInt("port")
  private lazy val client = HttpClient(ElasticsearchClientUri(host, port))

  /**
    * Elasticsearchから3時間以内のタグ情報を取得します
    *
    * @param ec
    * @return
    */
  def searchTags(implicit ec: ExecutionContextExecutor): Future[Seq[InstagramDto[InstagramMediaDto]]] = {
    val threeHoursAgo = DateUtil.toStrFormat(new DateTime().minusHours(3))
    val result: Future[SearchResponse] = client.execute {
      search(Index.tags).scroll(KeepAlive.tags).size(searchSize)
        .query(rangeQuery(FieldName.timestamp) gte threeHoursAgo)
        .query(boolQuery().must(termQuery(FieldName.isBan, false)))
        .sourceInclude(FieldName.tagName)
    }
    result.flatMap { r =>
      val searchHits = r.hits.hits.toSeq
      if (searchHits.isEmpty) {
        Future successful Seq(
          InstagramDto[InstagramMediaDto](
            dto = InstagramMediaDto(
              hashTag = "ファッション"
            )
          )
        )
      } else {
        getAll(r.scrollId, searchHits, KeepAlive.tags).flatMap { data =>
          Future successful data.map { d =>
            InstagramDto[InstagramMediaDto](
              dto = InstagramMediaDto(
                hashTag = d.sourceAsMap.getOrElse(FieldName.tagName, "").toString
              )
            )
          }.distinct
        }
      }
    }
  }

  /**
    * Elasticsearchから3時間以内の投稿情報に紐づくタグを取得します
    *
    * @param ec
    * @return
    */
  def searchTagsFromPosts(implicit ec: ExecutionContextExecutor): Future[Seq[InstagramDto[InstagramMediaDto]]] = {
    val threeHoursAgo = DateUtil.toStrFormat(new DateTime().minusHours(3))
    val result: Future[SearchResponse] = client.execute {
      search(Index.posts).scroll(KeepAlive.postsForTags).size(searchSize)
        .query(rangeQuery(FieldName.timestamp) gte threeHoursAgo).sourceInclude(FieldName.tagName)
    }
    result.flatMap { r =>
      val searchHits = r.hits.hits.toSeq
      val scrollId = r.scrollId
      getAll(scrollId, searchHits, KeepAlive.postsForTags).flatMap { data =>
        Future successful data.flatMap { d =>
          d.sourceAsMap(FieldName.tagName).asInstanceOf[Seq[String]].map { tag =>
            InstagramDto[InstagramMediaDto](
              dto = InstagramMediaDto(
                hashTag = tag.toString
              )
            )
          }
        }.distinct
      }
    }
  }

  /**
    * Elasticsearchへ投稿情報を保存します
    *
    * @param mediaDataEntities
    * @param ec
    * @return
    */
  def savePosts(mediaDataEntities: Seq[InstagramMediaDataEntity])(implicit ec: ExecutionContextExecutor): Future[Any] = {
    if (mediaDataEntities.nonEmpty) {
      val bulkData = mediaDataEntities.map { e =>
        indexInto(Index.posts)
          .id(e.mediaId)
          .fields(CaseClassUtil.getCCParams(e))
          .refresh(RefreshPolicy.WAIT_UNTIL)
      }
      client.execute(bulk(bulkData))
    } else Future successful()
  }

  /**
    * Elasticsearchへタグ情報を保存します
    *
    * @param tagEntity
    * @param ec
    * @return
    */
  def saveTag(tagEntity: InstagramHashTagEntity)(implicit ec: ExecutionContextExecutor): Future[IndexResponse] = {
    client.execute(
      indexInto(Index.tags)
        .id(tagEntity.tagName)
        .fields(CaseClassUtil.getCCParams(tagEntity))
    )
  }

  /**
    * 検索情報を再帰的に全件取得します
    *
    * @param scrollId
    * @param entities
    * @param keepAlive
    * @return
    */
  private def getAll(scrollId: Option[String], entities: Seq[SearchHit], keepAlive: String)(implicit ec: ExecutionContextExecutor): Future[Seq[SearchHit]] = {
    // scrollIdがあれば検索かける
    scrollId match {
      case Some(id) =>
        val sr = client.execute {
          searchScroll(id).keepAlive(keepAlive)
        }
        sr.flatMap { r =>
          val data = r.hits.hits.toSeq
          if (data.isEmpty) Future successful entities else getAll(r.scrollId, entities ++ data, keepAlive)
        }
      case None => Future successful entities
    }
  }

}

trait ElasticsearchConstants {
  val searchSize = 1000
  val constantPostSearchSize = 5000

  object FieldName {
    val timestamp = "timestamp"
    val likes = "likes"
    val tagName = "tagName"
    val isBan = "isBan"
    val accessToken = "accessToken"
    val mediaId = "mediaId"
    val mediaCount = "mediaCount"
    val userId = "userId"
  }

  object Index {
    val postsConstant = "posts"
    val posts: IndexAndType = postsConstant / "post"
    val tags: IndexAndType = "tags" / "tag"
  }

  object KeepAlive {
    val tags = "1m"
    val postsForUsers = "5m"
    val postsForTags = "10m"
  }

}