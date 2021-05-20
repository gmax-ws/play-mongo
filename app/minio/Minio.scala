package minio

import io.minio._
import io.minio.messages.Item

import java.lang

case class Minio(uri: String, accessKeyId: String, secretAccessKey: String) {

  val minioClient: MinioClient =
    MinioClient
      .builder()
      .endpoint(uri)
      .credentials(accessKeyId, secretAccessKey)
      .build()

  def makeBucket(bucketName: String): Unit =
    minioClient.makeBucket(MakeBucketArgs.builder.bucket(bucketName).build)

  def removeBucket(bucketName: String): Unit =
    minioClient.removeBucket(RemoveBucketArgs.builder.bucket(bucketName).build)

  def download(bucketName: String, objectName: String, fileName: String): Unit =
    minioClient.downloadObject(
      DownloadObjectArgs.builder
        .bucket(bucketName)
        .`object`(objectName)
        .filename(fileName)
        .build
    )

  def upload(
      bucketName: String,
      objectName: String,
      fileName: String
  ): ObjectWriteResponse =
    minioClient.uploadObject(
      UploadObjectArgs.builder
        .bucket(bucketName)
        .`object`(objectName)
        .filename(fileName)
        .build
    )

  def upload(
      bucketName: String,
      objectName: String,
      fileName: String,
      contentType: String
  ): ObjectWriteResponse =
    minioClient.uploadObject(
      UploadObjectArgs.builder
        .bucket(bucketName)
        .`object`(objectName)
        .filename(fileName)
        .contentType(contentType)
        .build
    )

  def delete(
      bucketName: String,
      objectName: String
  ): Unit =
    minioClient.removeObject(
      RemoveObjectArgs.builder
        .bucket(bucketName)
        .`object`(objectName)
        .build
    )

  def delete(
      bucketName: String,
      objectName: String,
      versionId: String,
      bypassRetentionMode: Boolean = false
  ): Unit =
    minioClient.removeObject(
      RemoveObjectArgs.builder
        .bucket(bucketName)
        .`object`(objectName)
        .versionId(versionId)
        .bypassGovernanceMode(bypassRetentionMode)
        .build
    )

  def listObjects(
      bucketName: String,
      recursive: Boolean = true
  ): lang.Iterable[Result[Item]] =
    minioClient.listObjects(
      ListObjectsArgs.builder().bucket(bucketName).recursive(recursive).build()
    )
}

object Minio extends App {
  def api = Minio("http://127.0.0.1:9000", "ZJbLwSLkT70mKEwj", "J6GffHhTCrm0jeW0ZQymTYZLR8LZ7w1N")
  def bucketName = "scalable-solutions"
  api.makeBucket(bucketName)
  api.upload(bucketName, "build.sbt", "build.sbt")
  api.download(bucketName, "build.sbt", "build-1.sbt")
}