## Knative user-registration

This repository holds source code for a demonstration of Knativce Serverless capabilities on OpenShift. It demonstrates basic features of Knative Serving and Eventing modules.

Here's the architecture of the application we're going to deploy and managed with Knative:

![Architecture](assets/architecture.png)

The application is made of following components:
* A `user-registration` microservice that is presenting a REST API for registering new users into a system,
* A Kafka broker that aims to persist the different events emitted by the `user-registration` component on registrations,
* A bunch of event-driven components that consumes events from Kafka to process the registration events.

In the demonstration scenario, we are going to deploy 2 differents revisions of the `user-registration` service to illustrate traffic distribution and canary release using Knative. Also we're going to use Knative Eventing concepts (`Source`, `Channel` and `Subscription`) to make the events consumers serverless too.

### Setup

So you should have an OpenShift 4.6+ cluster (or a Kubernetes one but it's trickier to setup) with the different features enabled:

* Strimzi.io Operator up and running for providing a Kafka broker to the app,
* Knative Serving and Eventing installed.

> If using OpenShift 4.5, be sure to install the OpenShift Serverless Operator using the latest `4.6` channel. Otherwise the CRD installed into the cluster won't have the correct API versions.

You should also have the `kn` CLI tool available on your laptop or bastion server:

```sh
$ kn version
Version:      v20201110-e8b26e18
Build Date:   2020-11-10 10:39:27
Git Revision: e8b26e18
Supported APIs:
* Serving
  - serving.knative.dev/v1 (knative-serving v0.18.0)
* Eventing
  - sources.knative.dev/v1alpha2 (knative-eventing v0.18.0)
  - eventing.knative.dev/v1beta1 (knative-eventing v0.18.0)
```

Start creating the new project to host our components and the dedicated broker:

```sh
oc new-project user-registration-serverless
oc create -f kafka-broker-openshift.yml -n user-registration-serverless
```

### Demonstration

#### Knative Serving

Deploy version `v1` of the `user-registration`:

```
$ kn service create user-registration --image quay.io/microcks/quarkus-user-registration:0.1 --env QUARKUS_PROFILE=kube --port 8383 -n user-registration-serverless
Creating service 'user-registration' in namespace 'user-registration-serverless':

  0.039s The Configuration is still working to reflect the latest desired specification.
  0.106s The Route is still working to reflect the latest desired specification.
  0.229s Configuration "user-registration" is waiting for a Revision to become ready.
  9.660s ...
  9.749s Ingress has not yet been reconciled.
  9.850s unsuccessfully observed a new generation
 10.021s Ready to serve.

Service 'user-registration' created to latest revision 'user-registration-wjhqk-1' is available at URL:
http://user-registration-user-registration-serverless.apps.cluster-7eee.7eee.example.opentlc.com

$ kn service update user-registration --tag user-registration-wjhqk-1=v1
Updating Service 'user-registration' in namespace 'user-registration-serverless':

  0.031s The Route is still working to reflect the latest desired specification.
  0.114s Ingress has not yet been reconciled.
  0.136s unsuccessfully observed a new generation
  0.511s Ready to serve.

Service 'user-registration' with latest revision 'user-registration-wjhqk-1' (unchanged) is available at URL:
http://user-registration-user-registration-serverless.apps.cluster-7eee.7eee.example.opentlc.com

$ export APP_URL=`oc get ksvc user-registration -o json -n user-registration-serverless | jq -r '.status.url'`
```

Play with App through CURL command, registering new users and see the Pods scaling-up and down:

```sh
$ curl -XPOST $APP_URL/register -H 'Content-type: application/json' -d '{"fullName":"Laurent Broudoux","email":"laurent.broudoux@gmail.com","age":41}' -s | jq .
{
  "age": 41,
  "email": "laurent.broudoux@gmail.com",
  "fullName": "Laurent Broudoux",
  "id": "c637a5f8-ecfe-45be-b44d-69bf08043603"
}
```

We've got `v1` responses so far with just 4 properties in the response. Deploy version `v2` that adds a new property and send events to Kafka. Do not put traffic on this new revision.

```sh
$ kn service update user-registration --image quay.io/microcks/quarkus-user-registration:latest --traffic @latest=0 --traffic v1=100
Updating Service 'user-registration' in namespace 'user-registration-serverless':

  0.060s The Configuration is still working to reflect the latest desired specification.
  6.794s Traffic is not yet migrated to the latest revision.
  6.871s Ingress has not yet been reconciled.
  6.978s Ready to serve.

Service 'user-registration' updated to latest revision 'user-registration-mwmrh-3' is available at URL:
http://user-registration-user-registration-serverless.apps.cluster-7eee.7eee.example.opentlc.com
```

From here, we want to split traffic between the 2 revisions to do a canary release. You can do that through:

the OpenShift console UI, tagging the new revision with `v2` (see below image),

![Traffic distribution](assets/traffic-splitting.png)

or through the command line (see below commands):

``` sh
$ kn service update user-registration --tag user-registration-mwmrh-3=v2
$ kn service update user-registration --traffic v2=10 --traffic v1=90
```

You can retrieve the specific `v2` url and check everything is fine:

```sh
$ export APP_V2_URL=`oc get ksvc user-registration -o json -n user-registration-serverless | jq -r '.status.traffic[1].url'`

$ curl -XPOST $APP_V2_URL/register -H 'Content-type: application/json' -d '{"fullName":"Laurent Broudoux","email":"laurent.broudoux@gmail.com","age":41}' -s | jq .
{
  "age": 41,
  "email": "laurent.broudoux@gmail.com",
  "fullName": "Laurent Broudoux",
  "id": "a34d49f4-5332-4919-bd00-35089af32ee4",
  "registrationDate": "1606493455380"
}
```

Now that you're happy, move traffic from `v1` to `v2`

```sh
$ curl -XPOST $APP_URL/register -H 'Content-type: application/json' -d '{"fullName":"Laurent Broudoux","email":"laurent.broudoux@gmail.com","age":41}' -s | jq .
{
  "age": 41,
  "email": "laurent.broudoux@gmail.com",
  "fullName": "Laurent Broudoux",
  "id": "bba6e464-4cb3-4361-ad20-0e3294bdb00f"
}

$ kn service update user-registration --traffic v1=0,v2=100
Updating Service 'user-registration' in namespace 'user-registration-serverless':

  0.057s The Route is still working to reflect the latest desired specification.
  0.134s Ingress has not yet been reconciled.
  0.235s unsuccessfully observed a new generation
  0.591s Ready to serve.

Service 'user-registration' with latest revision 'user-registration-mwmrh-3' (unchanged) is available at URL:
http://user-registration-user-registration-serverless.apps.cluster-7eee.7eee.example.opentlc.com

$ curl -XPOST $APP_URL/register -H 'Content-type: application/json' -d '{"fullName":"Laurent Broudoux","email":"laurent.broudoux@gmail.com","age":41}' -s | jq .
{
  "age": 41,
  "email": "laurent.broudoux@gmail.com",
  "fullName": "Laurent Broudoux",
  "id": "fafc43d4-46e3-4595-b5d5-e17f31ae3fac",
  "registrationDate": "1606493796914"
}
```

#### Knative Eventing

Create the `Source` and the `Channel`

```sh
oc create -f user-signed-up-channel.yml -n user-registration-serverless
oc create -f user-signed-up-source.yml -n user-registration-serverless
```

Deploy the `user-registration-consumer`

```sh
$ kn service create user-registration-consumer --image quay.io/lbroudoux/user-registration-consumer:latest --port 8484 -n user-registration-serverless
```

Deploy some other consumer...

```sh
$ kn service create event-display --image gcr.io/knative-releases/knative.dev/eventing-contrib/cmd/event_display
```

Create `Subscriptions` to bind the `Channel` to our consumers. You can do that using the OpenShift Console DEveloper UI by drag-n-dropping connections between the `Channel` and the Knative Services or with this commands:

```sh
oc create -f event-display-subscription.yml -n user-registration-serverless
oc create -f user-registration-consumer-subscription.yml -n user-registration-serverless
```

Now it's time to demonstrate everything altogether! Be sure that you waited long enough so that there's no pod still running on the dofferent microservices. You should have something like that:

![Scale Zero](assets/scale-zero.png)

Then send a request to the App and you should see erveything light-up starting with the `user-registration` component ans just after the 2 events consumers:

```sh
$ curl -XPOST $APP_URL/register -H 'Content-type: application/json' -d '{"fullName":"Laurent Broudoux","email":"laurent.broudoux@gmail.com","age":41}' -s | jq .
{
  "age": 41,
  "email": "laurent.broudoux@gmail.com",
  "fullName": "Laurent Broudoux",
  "id": "da4acd59-044b-4c8f-a607-3b568c7cee27",
  "registrationDate": "1606751232863"
}
```

![Scale Up](assets/scale-up.png)

Check the logs in the `event-display-*` pod and you should have something like:

```
☁️  cloudevents.Event
Validation: valid
Context Attributes,
  specversion: 1.0
  type: dev.knative.kafka.event
  source: /apis/v1/namespaces/user-registration-serverless/kafkasources/kafka-source#user-signed-up
  subject: partition:0#3
  id: partition:0/offset:3
  time: 2020-11-30T15:47:12.87Z
Extensions,
  key: 1606751232867
  knativehistory: channel-kn-channel.user-registration-serverless.svc.cluster.local
  traceparent: 00-2269cb19c9a73754700612cc3f3fc22d-8714cfce7963d7b4-00
Data,
  {"age":41,"email":"laurent.broudoux@gmail.com","fullName":"Laurent Broudoux","id":"da4acd59-044b-4c8f-a607-3b568c7cee27","sendAt":"1606751232867"}
```

Check the logs in the `user-registration-consumer-*` pod and you should have something like:

```
__  ____  __  _____   ___  __ ____  ______
 --/ __ \/ / / / _ | / _ \/ //_/ / / / __/
 -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \
--\___\_\____/_/ |_/_/|_/_/|_|\____/___/
2020-11-30 15:47:15,145 INFO  [io.quarkus] (main) user-registration-consumer 1.0-SNAPSHOT native (powered by Quarkus 1.9.2.Final) started in 0.012s. Listening on: http://0.0.0.0:8484
2020-11-30 15:47:15,145 INFO  [io.quarkus] (main) Profile prod activated.
2020-11-30 15:47:15,145 INFO  [io.quarkus] (main) Installed features: [cdi, resteasy, resteasy-jackson]
2020-11-30 15:47:15,868 INFO  [org.acm.reg.UserRegistrationResource] (executor-thread-1) cloudEventJson: {"age":41,"email":"laurent.broudoux@gmail.com","fullName":"Laurent Broudoux","id":"da4acd59-044b-4c8f-a607-3b568c7cee27","sendAt":"1606751232867"}
2020-11-30 15:47:15,868 INFO  [org.acm.reg.UserRegistrationResource] (executor-thread-1) Processing registration {da4acd59-044b-4c8f-a607-3b568c7cee27} for {Laurent Broudoux}
```

### Troubeshoot

#### Check on Kafka broker

```sh
$ oc exec -n user-registration-serverless -it my-cluster-kafka-0 -- /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --from-beginning \
    --property print.key=true \
    --topic user-signed-up

$ oc exec -n user-registration-serverless -it my-cluster-kafka-0 -- /opt/kafka/bin/kafka-console-producer.sh \
    --broker-list my-cluster-kafka-bootstrap:9092 \
    --topic user-signed-up
```
