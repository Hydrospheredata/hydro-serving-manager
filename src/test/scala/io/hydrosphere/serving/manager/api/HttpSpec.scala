package io.hydrosphere.serving.manager.api

import cats.implicits._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.deploy_config._
import skuber.LabelSelector
import skuber.Pod.{Affinity, EqualToleration, ExistsToleration, TolerationEffect}
import skuber.Pod.Affinity.{NodeAffinity, NodeSelectorOperator, NodeSelectorRequirement, NodeSelectorTerm, PodAffinity, PodAffinityTerm, PodAntiAffinity, WeightedPodAffinityTerm}
import skuber.Pod.Affinity.NodeAffinity.{PreferredSchedulingTerm, RequiredDuringSchedulingIgnoredDuringExecution}
import spray.json._

class HttpSpec extends GenericUnitTest {
  describe("DeploymentConfiguraion API") {
    it("should return a json") {
      val container = K8sContainerConfig(
        requirements = Requirements(
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

      val result = DeploymentConfiguration(
        name = "cool-deployment-config",
        container = container,
        pod = pod,
        deployment = deployment,
        hpa = hpa
      )

      println(result.toJson.compactPrint)

      succeed
    }
  }
}
