package io.hydrosphere.serving.manager.config

import cats.Show
import cats.derived.semiauto
import com.zaxxer.hikari.HikariConfig
import io.hydrosphere.serving.manager.util.Secret

import scala.concurrent.duration.MINUTES

case class HikariConfiguration(
    jdbcUrl: String,
    username: String,
    password: Secret[String],
    driverClassname: String = "org.postgresql.Driver",
    maximumPoolSize: Int,
    initializationFailTimeout: Long = 20000L,
    leakDetectionThreshold: Long = 60000L,
    maxLifetime: Long = MINUTES.toMillis(30),
    registerMbeans: Boolean = true,
    connectionTimeout: Long = 30000,
    poolName: String = "manager-db-pool"
)

object HikariConfiguration {
  implicit val db: Show[HikariConfiguration] = semiauto.show

  def toConfig(config: HikariConfiguration): HikariConfig = {
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.jdbcUrl)
    hikariConfig.setUsername(config.username)
    hikariConfig.setPassword(config.password.value)
    hikariConfig.setDriverClassName(config.driverClassname)
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize)
    hikariConfig.setInitializationFailTimeout(config.initializationFailTimeout)
    hikariConfig.setLeakDetectionThreshold(config.leakDetectionThreshold)
    hikariConfig.setMaxLifetime(config.maxLifetime)
    hikariConfig.setPoolName(config.poolName)
    hikariConfig.setRegisterMbeans(config.registerMbeans)
    hikariConfig.setConnectionTimeout(config.connectionTimeout)
    hikariConfig
  }
}
