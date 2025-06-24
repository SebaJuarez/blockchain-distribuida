#!/bin/bash
kubectl apply -f deploy/namespaces/
kubectl apply -f deploy/redis-rabbit/
kubectl apply -f deploy/secrets/
kubectl apply -f deploy/serviceAccount/
kubectl apply -f deploy/services/
kubectl apply -f deploy/deployment/