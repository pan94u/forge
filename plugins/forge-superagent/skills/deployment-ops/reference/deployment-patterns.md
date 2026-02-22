# Deployment Patterns — K8s/Istio Reference

## Rolling Update (Kubernetes)

```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
```

Rollback: `kubectl rollout undo deployment/<service>`

## Blue-Green (Ingress Switch)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
spec:
  rules:
    - host: service.example.com
      http:
        paths:
          - path: /
            backend:
              service:
                name: service-green  # switch from service-blue
                port: { number: 80 }
```

## Canary (Istio VirtualService)

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
spec:
  http:
    - route:
        - destination: { host: service, subset: stable }
          weight: 95
        - destination: { host: service, subset: canary }
          weight: 5
```
