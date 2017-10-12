## Summary

Crawler service by akka stream.

## Elasticsearch

### How to setup

```
# 1. Install Elasticsearch
curl -L -O https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-5.6.1.tar.gz
tar -xvf elasticsearch-5.6.1.tar.gz

# 2. Intall plugins => Kuromoji & ICU And enable Script
cd elasticsearch-5.6.1
./bin/elasticsearch-plugin install analysis-kuromoji
./bin/elasticsearch-plugin install analysis-icu
echo "script.inline: true" >> config/elasticsearch.yml
```
