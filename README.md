## Knative user-registration

### Setup

```sh
oc new-project user-regsitration-serverless
oc create -f kafka-broker-openshift.yml -n user-registration-serverless
```

### Demonstration

#### Knative Serving

Deploy version `v1`

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

$ curl -XPOST $APP_URL/register -H 'Content-type: application/json' -d '{"fullName":"Laurent Broudoux","email":"laurent.broudoux@gmail.com","age":41}' -s | jq .
{
  "age": 41,
  "email": "laurent.broudoux@gmail.com",
  "fullName": "Laurent Broudoux",
  "id": "c637a5f8-ecfe-45be-b44d-69bf08043603"
}
```

Deploy version `v2` and put 10% traffic

```sh
$ kn service update user-registration --image quay.io/microcks/quarkus-user-registration:latest --traffic @latest=0 --traffic v1=100
Updating Service 'user-registration' in namespace 'user-registration-serverless':

  0.060s The Configuration is still working to reflect the latest desired specification.
  6.794s Traffic is not yet migrated to the latest revision.
  6.871s Ingress has not yet been reconciled.
  6.978s Ready to serve.

Service 'user-registration' updated to latest revision 'user-registration-mwmrh-3' is available at URL:
http://user-registration-user-registration-serverless.apps.cluster-7eee.7eee.example.opentlc.com

$ kn service update user-registration --tag user-registration-mwmrh-3=v2
$ kn service update user-registration --traffic v2=10 --traffic v1=90


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

Move traffic from `v1` to `v2`

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
oc create -f user-signed-up-source.yml -n user-registration-serverless
```

Deploy the `user-regsistration-consumer`

```sh
$ kn service create user-registration-consumer --image quay.io/lbroudoux/user-registration-consumer:latest --port 8484 -n user-registration-serverless
```

Deploy some other consumer...

```sh
$ kn service create event-display --image gcr.io/knative-releases/knative.dev/eventing-contrib/cmd/event_display
```

Create `Subscriptions` to bind the `Channel` to our consumers.

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

#### Check Knative Channel

```
☁️  cloudevents.Event
Validation: valid
Context Attributes,
  specversion: 1.0
  type: dev.knative.sources.ping
  source: /apis/v1/namespaces/user-registration-serverless/pingsources/event-greeter-ping-source
  id: 7bd7b967-2d76-4fe0-9480-081bab0c6e3f
  time: 2020-11-27T10:50:00.000136175Z
  datacontenttype: application/json
Extensions,
  knativehistory: channel-kn-channel.user-registration-serverless.svc.cluster.local
Data,
  {
    "message": "Thanks for doing Knative Tutorial"
  }


curl localhost:8484 -H 'Content-type: application/json' -XPOST -d '{"specversion" : "1.0", "type" : "com.github.pull.create", "source" : "https://github.com/cloudevents/spec/pull", "subject" : "123", "data": "{\"id\": \"smdfjlmkfjsmlfd\", \"fullName\":\"Laurent Broudoux\",\"email\":\"laurent.broudoux@gmail.com\",\"age\":45}"}'




```
