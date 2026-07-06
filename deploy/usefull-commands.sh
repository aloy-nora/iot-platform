kind load docker-image emqx/emqx:5.10.4 --name iot
kind load docker-image tdengine/tsdb:3.3.7.0 --name iot
kind load docker-image redis:7-alpine --name iot
kind load docker-image mysql:8.0 --name iot
docker exec iot-control-plane crictl images | grep mysql

kubectl delete pod -n iot -l app=mysql
kubectl get pods -n iot -w

kubectl exec -it -n iot deploy/mysql -- mysql -uroot -pexample123 -e "show databases;"
kubectl exec -it -n iot deploy/mysql -- mysql -uroot -pexample123 iot \
  -e "CREATE TABLE t(id INT); INSERT INTO t VALUES(42); SELECT * FROM t;"
kubectl exec -it -n iot deploy/mysql -- mysql -uroot -pexample123 iot -e "SELECT * FROM t;"

kubectl rollout restart deploy/redis -n iot
kubectl exec -it -n iot deploy/redis -- redis-cli ping
kubectl exec -it -n iot deploy/redis -- redis-cli set foo bar
kubectl exec -it -n iot deploy/redis -- redis-cli get foo

docker build -t iot-backend:dev .
kind load docker-image iot-backend:dev --name iot
kubectl apply -f ~/Workshop/iot-platform/deploy/k8s/
kubectl apply -f deploy/k8s/20-backend.yaml

kubectl exec -it -n iot deploy/tdengine -- taos -s "CREATE DATABASE iot;"

kubectl exec -i -n iot deploy/mysql -- mysql -uroot -pexample123 iot < sql/03_mysql_schema.sql
kubectl exec -i -n iot deploy/mysql -- mysql -uroot -pexample123 iot < sql/04_alarm_schema.sql
kubectl exec -i -n iot deploy/tdengine -- taos < sql/01_schema.sql
kubectl exec -it -n iot deploy/mysql -- mysql -uroot -pexample123 iot -e "SHOW TABLES;"
kubectl exec -it -n iot deploy/tdengine -- taos -s "USE iot; SHOW STABLES;"

kubectl exec -n iot deploy/tdengine -- taos -s "CREATE STABLE IF NOT EXISTS iot.env_sensor (ts TIMESTAMP, temp FLOAT, humi FLOAT, light INT, soil_ph FLOAT) TAGS (device_id NCHAR(32), location NCHAR(64), dev_type NCHAR(16));"
kubectl exec -n iot deploy/tdengine -- taos -s "SHOW iot.STABLES;"

kubectl delete namespace iot
helm install iot deploy/helm/iot-platform -n iot --create-namespace
kubectl get pods -n iot -w

kubectl exec -it -n iot deploy/mysql -- mysql -uroot -pexample123 iot -e "SHOW TABLES;"
kubectl exec -n iot deploy/tdengine -- taos -s "SHOW iot.STABLES;"
kubectl logs -n iot -l app=backend --tail=5

helm list -n iot
helm status iot -n iot
helm upgrade iot deploy/helm/iot-platform -n iot
helm uninstall iot -n iot
