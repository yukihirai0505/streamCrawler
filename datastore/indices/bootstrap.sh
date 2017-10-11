#!/bin/sh

ELASTICSEARCH_HOST="localhost"
ELASTICSEAECH_PORT="9200"
ELASTICSEAECH_URL="${ELASTICSEARCH_HOST}:${ELASTICSEAECH_PORT}"

# 1. ディレクトリの配列をつくる
TARGETS=./*
FILE_ARR=()
DIR_ARR=()
for FILE_PATH in $TARGETS; do
  if [ -d $FILE_PATH ] ; then
    DIR_ARR+=($(basename $FILE_PATH))
  fi
done


# 2. yaml2jsonでanalysis.ymlをanalysis.jsonに変更する
yaml2json analysis.yml > analysis.json

# 3. それぞれのインデックスを削除して作成する
for INDEX in ${DIR_ARR[@]}; do

  URL="${ELASTICSEAECH_URL}/${INDEX}"
  curl -XDELETE ${URL}

  echo "\n${INDEX}のインデックスを削除しました。\n"

  curl -XPUT ${URL} -d @analysis.json

  echo "\n${INDEX}のインデックスを作成しました。\n"

  # 4. それぞれのディレクトリに移動してファイル名を取得後 yaml2jsonを実行 して タイプのマッピングの定義を作成して jsonファイルを削除する
  cd ./${INDEX}
  FILES=./*
  for FILE in $FILES; do
    if [ -f $FILE ] ; then
      TYPE=$(basename $FILE | sed 's/\.[^\.]*$//')
      yaml2json ${TYPE}.yml > ${TYPE}.json
      curl -XPUT "localhost:9200/${INDEX}/${TYPE}/_mapping" -d @${TYPE}.json
      echo "\n${INDEX}に${TYPE}のタイプを作成しました。\n"
    fi
  done
  cd ../
done

# 5. jsonを削除する
rm analysis.json ./*/*.json

NOW=$(date '+%Y-%m-%dT%H:%M:%S+09:00')
echo $NOW

# 6. 初期データの作成
curl -H "Content-Type: application/json" --request POST 'localhost:9200/tags/tag/%E3%83%95%E3%82%A1%E3%83%83%E3%82%B7%E3%83%A7%E3%83%B3' -d '{"tagName":"ファッション", "timestamp": "'${NOW}'"}'

echo "\n初期データを作成しました。\n"
