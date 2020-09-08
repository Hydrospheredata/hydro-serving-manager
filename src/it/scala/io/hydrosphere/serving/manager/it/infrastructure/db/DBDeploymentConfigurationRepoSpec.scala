package io.hydrosphere.serving.manager.it.infrastructure.db

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import doobie.scalatest.IOChecker
import io.hydrosphere.serving.manager.domain.deploy_config.{DeploymentConfiguration, K8sContainerConfig, K8sDeploymentConfig, K8sHorizontalPodAutoscalerConfig, K8sPodConfig, Requirement, Requirements}
import io.hydrosphere.serving.manager.infrastructure.db.repository.DBDeploymentConfigurationRepository
import io.hydrosphere.serving.manager.it.FullIntegrationSpec
import skuber.LabelSelector
import skuber.Pod.Affinity.NodeAffinity.{PreferredSchedulingTerm, RequiredDuringSchedulingIgnoredDuringExecution}
import skuber.Pod.Affinity.{NodeAffinity, NodeSelectorOperator, NodeSelectorRequirement, NodeSelectorTerm, PodAffinity, PodAffinityTerm, PodAntiAffinity, WeightedPodAffinityTerm}
import skuber.Pod.{Affinity, EqualToleration, ExistsToleration, TolerationEffect}

class DBDeploymentConfigurationRepoSpec extends FullIntegrationSpec with IOChecker {

  describe("Queries") {
    val container = K8sContainerConfig(
      resources = Requirements(
        limits = Requirement(
          cpu = "2",
          memory = "2g"
        ).some,
        requests = Requirement(
          cpu = "2",
          memory = "2g"
        ).some
      ).some
    ).some
    val podRequired = RequiredDuringSchedulingIgnoredDuringExecution(
      nodeSelectorTerms = List(NodeSelectorTerm(
        matchExpressions = List(NodeSelectorRequirement(
          key = "exp1",
          operator = NodeSelectorOperator.Exists,
          values = List("a", "b", "c")
        )),
        matchFields = List(NodeSelectorRequirement(
          key = "fields1",
          operator = NodeSelectorOperator.Exists,
          values = List("aa", "bb", "cc")
        )),
      ))
    ).some
    val podPreferred = List(
      PreferredSchedulingTerm(
        preference = NodeSelectorTerm(
          matchExpressions = List(NodeSelectorRequirement(
            key = "exp2",
            operator = NodeSelectorOperator.Exists,
            values = List("aaaa", "bvzv", "czxc")
          )),
          matchFields = List(NodeSelectorRequirement(
            key = "fields3",
            operator = NodeSelectorOperator.Exists,
            values = List("aaa", "cccc", "zxcc")
          )),
        ),
        weight = 100
      )
    )
    val nodeAffinity = NodeAffinity(
      requiredDuringSchedulingIgnoredDuringExecution = podRequired,
      preferredDuringSchedulingIgnoredDuringExecution = podPreferred
    ).some
    val podAffinity = PodAffinity(
      requiredDuringSchedulingIgnoredDuringExecution = List(
        PodAffinityTerm(
          labelSelector = LabelSelector(
            LabelSelector.ExistsRequirement("kek"),
            LabelSelector.NotInRequirement("key", List("a", "b")),
            LabelSelector.NotExistsRequirement("kek"),
          ).some,
          namespaces = List("namespace1"),
          topologyKey = "top"
        )
      ),
      preferredDuringSchedulingIgnoredDuringExecution = List(
        WeightedPodAffinityTerm(
          weight = 100,
          podAffinityTerm = PodAffinityTerm(
            labelSelector = LabelSelector(
              LabelSelector.InRequirement("kek", List("a", "b")),
              LabelSelector.IsEqualRequirement("key", "a"),
              LabelSelector.IsNotEqualRequirement("kek", "b"),
            ).some,
            namespaces = List("namespace2"),
            topologyKey = "toptop"
          )
        )
      )
    ).some
    val podAntiAffinity = PodAntiAffinity(
      requiredDuringSchedulingIgnoredDuringExecution = List(
        PodAffinityTerm(
          labelSelector = LabelSelector(
            LabelSelector.ExistsRequirement("kek"),
            LabelSelector.NotInRequirement("key", List("a", "b")),
            LabelSelector.NotExistsRequirement("kek"),
          ).some,
          namespaces = List("namespace1"),
          topologyKey = "top"
        )
      ),
      preferredDuringSchedulingIgnoredDuringExecution = List(
        WeightedPodAffinityTerm(
          weight = 100,
          podAffinityTerm = PodAffinityTerm(
            labelSelector = LabelSelector(
              LabelSelector.InRequirement("kek", List("a", "b")),
              LabelSelector.IsEqualRequirement("key", "a"),
              LabelSelector.IsNotEqualRequirement("kek", "b"),
            ).some,
            namespaces = List("namespace2"),
            topologyKey = "toptop"
          )
        )
      )
    ).some
    val podConfigAffinity = Affinity(
      nodeAffinity = nodeAffinity,
      podAffinity = podAffinity,
      podAntiAffinity = podAntiAffinity
    )
    val podTolerations = List(
      EqualToleration(
        key = "equalToleration",
        value = "kek".some,
        effect = TolerationEffect.PreferNoSchedule.some,
        tolerationSeconds = 30.some
      ),
      ExistsToleration(
        key = "equalToleration".some,
        effect = TolerationEffect.PreferNoSchedule.some,
        tolerationSeconds = 30.some
      )
    )
    val pod = K8sPodConfig(
      nodeSelector = Map(
        "im" -> "a map",
        "foo" -> "bar"
      ).some,
      affinity = podConfigAffinity.some,
      tolerations = podTolerations
    ).some
    val deployment = K8sDeploymentConfig(
      replicaCount = 4.some
    ).some
    val hpa = K8sHorizontalPodAutoscalerConfig(
      minReplicas = 2.some,
      maxReplicas = 10,
      cpuUtilization = 80.some
    ).some
    val depConf = DeploymentConfiguration(
      name = "cool-deployment-config",
      container = container,
      pod = pod,
      deployment = deployment,
      hpa = hpa
    )

    it("should be ok") {
      check(DBDeploymentConfigurationRepository.allQ)
      check(DBDeploymentConfigurationRepository.deleteQ("hey"))
      check(DBDeploymentConfigurationRepository.getByNameQ("hey"))
      check(DBDeploymentConfigurationRepository.insertQ(depConf))
      check(DBDeploymentConfigurationRepository.getManyQ(NonEmptyList.of("hey1", "hey2")))
      succeed
    }
  }
  describe("Methods") {
    pending
  }

  override def transactor: doobie.Transactor[IO] = app.transactor
}
