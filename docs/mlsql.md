## MLSQL

MLSQL通过添加了两个特殊语法，使得SQL也能够支持机器学习。


### 模型训练

语法：

```sql
-- 从tableName获取数据，通过where条件对Algorithm算法进行参数配置并且进行模型训练，最后
-- 训练得到的模型会保存在path路径。
train [tableName] as [Algorithm].[path] where [booleanExpression]
```

比如：

```sql
train data as RandomForest.`/tmp/model` where inputCol="featrues" and maxDepth="3"
```

这句话表示使用对表data中的featrues列使用RandomForest进行训练，树的深度为3。训练完成后的模型会保存在`tmp/model`。

很简单对么？

如果需要知道算法的输入格式以及算法的参数,可以参看[Spark MLlib](https://spark.apache.org/docs/latest/ml-guide.html)。
在MLSQL中，输入格式和算法的参数和Spark MLLib保持一致。

### 样本不均衡问题

为了解决样本数据不平衡问题，所有模型（目前只支持贝叶斯）都支持一种特殊的训练方式。假设我们是一个二分类，A,B。 A 分类有100个样本，B分类有1000个。
差距有十倍。为了得到一个更好的训练效果，我们会训练十个（最大样本数/最小样本数）模型。

第一个模型：

A拿到100,从B随机抽样10%(100/1000),训练。

重复第一个模型十次。

这个可以通过在where条件里把multiModels="true" 即可开启。

在预测函数中，会自动拿到置信度最高模型作为预测结果。


### 预测

语法：

```sql
-- 从Path中加载Algorithm算法对应的模型，并且将该模型注册为一个叫做functionName的函数。
register [Algorithm].[Path] as functionName;
```

比如：

```
register RandomForest.`/tmp/zhuwl_rf_model` as zhuwl_rf_predict;
```

接着我就可以在SQL中使用该函数了：

```
select zhuwl_rf_predict(features) as predict_label, label as original_label from sample_table;
```

很多模型会有多个预测函数。假设我们名字都叫predict

LDA 有如下函数：

* predict  参数为一次int类型，返回一个主题分布。
* predict_doc 参数为一个int数组，返回一个主题分布




### Word2vec

假设"/tmp/test.csv"内容为：

```
body
a b c
a d m
j d c
a b c
b b c
```

通过Word2vec可以为里面每个字符计算一个向量。

示例:

```sql
load csv.`/tmp/test.csv` options header="True" as ct;

select split(body," ") as words from ct as new_ct;

train new_ct as word2vec.`/tmp/w2v_model` where inputCol="words" and minCount="0";

register word2vec.`/tmp/w2v_model` as w2v_predict;

select words[0] as w, w2v_predict(words[0]) as v from new_ct as result;

save overwrite result as json.`/tmp/result`;

```

### StringIndex

StringIndex可以给每个词汇生成一个唯一的ID。

```sql
load csv.`/tmp/abc.csv` options header="True" as data;
select explode(split(body," ")) as word from data as new_dt;
train new_dt as StringIndex.`/tmp/model` where inputCol="word";
register StringIndex.`/tmp/model` as predict;
select predict_r(1)  from new_dt as result;
save overwrite result as json.`/tmp/result`;
```

除了你注册的predict函数以外，StringIndex会隐式给你生成一些函数，包括：

* predict    参数为一个词汇，返回一个数字
* predict_r  参数为一个数字，返回一个词
* predict_array 参数为词汇数组，返回数字数组
* predict_rarray 参数为数字数组，返回词汇数组

### TfIdf

```sql
--  加载文本数据
load csv.`/tmp/test.csv` options header="True" 
as zhuhl_ct;

--分词
select split(body," ") as words from zhuhl_ct 
as zhuhl_new_ct;

-- 把文章转化为数字序列，因为tfidf模型需要数字序列

train word_table as StringIndex.`/tmp/zhuhl_si_model` where 
inputCol="word" and handleInvalid="skip";

register StringIndex.`/tmp/zhuhl_si_model` as zhuhl_si_predict;

select zhuhl_si_predict_array(words) as int_word_seq from zhuhl_new_ct
as int_word_seq_table;

-- 训练一个tfidf模型
train int_word_seq_table as TfIdf.`/tmp/zhuhl_tfidf_model` where 
inputCol="words"
and numFeatures="100" 
and binary="true";

--注册tfidf模型
register TfIdf.`/tmp/zhuhl_tfidf_model` as zhuhl_tfidf_predict;

--将文本转化为tf/idf向量
select zhuhl_tfidf_predict(int_word_seq) as features from int_word_seq_table
as lda_data;

```

通过tf/idf模型预测得到的就是向量，可以直接被其他算法使用。和libsvm 格式数据一致。

### NaiveBayes

示例：

```sql
load libsvm.`/spark-2.2.0-bin-hadoop2.7/data/mllib/sample_libsvm_data.txt` as data;
train data as NaiveBayes.`/tmp/bayes_model`;
register NaiveBayes.`/tmp/bayes_model` as bayes_predict;
select bayes_predict(features)  from data as result;
save overwrite result as json.`/tmp/result`;
```

### RandomForest

```sql
load libsvm.`/spark-2.2.0-bin-hadoop2.7/data/mllib/sample_libsvm_data.txt` as data;
train data as RandomForest.`/tmp/model`;
register RandomForest.`/tmp/model` as predict;
select predict(features)  from data as result;
save overwrite result as json.`/tmp/result`;
```

### GBTRegressor

```sql
load libsvm.`/spark-2.2.0-bin-hadoop2.7/data/mllib/sample_libsvm_data.txt` as data;
train data as GBTRegressor.`/tmp/model`;
register GBTRegressor.`/tmp/model` as predict;
select predict(features)  from data as result;
save overwrite result as json.`/tmp/result`;

```


### LDA 

```sql
load libsvm.`/spark-2.2.0-bin-hadoop2.7/data/mllib/sample_lda_libsvm_data.txt` as data;
train data as LDA.`/tmp/model` where k="10" and maxIter="10";
register LDA.`/tmp/model` as predict;
select predict_v(features)  from data as result;
save overwrite result as json.`/tmp/result`;
```

### KMeans

```sql
load libsvm.`/spark-2.2.0-bin-hadoop2.7/data/mllib/sample_kmeans_data.txt` as data;
train data as KMeans.`/tmp/model` where k="10";
register KMeans.`/tmp/model` as predict;
select predict(features)  from data as result;
save overwrite result as json.`/tmp/result`;
```


### FPGrowth

abc.csv:

```
body
1 2 5
1 2 3 5
1 2
```

示例:

```sql
load csv.`/tmp/abc.csv` options header="True" as data;
select split(body," ") as items from data as new_dt;
train new_dt as FPGrowth.`/tmp/model` where minSupport="0.5" and minConfidence="0.6" and itemsCol="items";
register FPGrowth.`/tmp/model` as predict;
select predict(items)  from new_dt as result;
save overwrite result as json.`/tmp/result`;
```




### GBTs

```sql
load libsvm.`/spark-2.2.0-bin-hadoop2.7/data/mllib/sample_libsvm_data.txt` as data;
train data as GBTs.`/tmp/model`;
register GBTs.`/tmp/model` as predict;
select predict(features)  from data as result;
save overwrite result as json.`/tmp/result`;
```

### LSVM

```sql
load libsvm.`/Users/allwefantasy/Softwares/spark-2.2.0-bin-hadoop2.7/data/mllib/sample_libsvm_data.txt` as data;
train data as LSVM.`/tmp/model` where maxIter="10" and regParam="0.1";
register LSVM.`/tmp/model` as predict;
select predict(features)  from data as result;
save overwrite result as json.`/tmp/result`;
```


### RowMatrix

当你想计算向量两两相似的时候，RowMatrix提供了一个快速高效的方式。比如LDA计算出了每个文档的向量分布，接着我们需要任意给定一个文章，然后找到
相似的文章， 则可以使用RowMatrix进行计算。无论如何，当文档到百万，计算量始终都是很大的（没有优化前会有万亿次计算），所以实际使用可以将数据
先简单分类。之后在类里面再做相似度计算。

另外RowMatrix需要两个字段，一个是向量，一个是数字，数字必须是从0开始递增，用来和矩阵行做对应，方便唯一标记一篇内容。
如果你的数据不是这样的，比如你的文档的唯一编号是断开的或者是一个字符串，那么你可以使用StringIndex 去生成一个字段作为唯一标记。

```sql
load libsvm.`/spark-2.2.0-bin-hadoop2.7/data/mllib/sample_lda_libsvm_data.txt` as data;
train data as LDA.`/tmp/model` where k="10" and maxIter="10";

register LDA.`/tmp/model` as predict;

select *,zhuhl_lda_predict_doc(features) as features from data
as zhuhl_doclist;

train zhuhl_doclist as RowMatrix.`/tmp/zhuhl_rm_model` where inputCol="features" and labelCol="label";
register RowMatrix.`/tmp/zhuhl_rm_model` as zhuhl_rm_predict;

select zhuhl_rm_predict(label) from data
```

### PageRank

PageRank 可以对有关系对的东西进行建模，输入也很简答，只要item-item pair 就行。

```
load csv.`/spark-2.2.0-bin-hadoop2.7/data/mllib/pagerank_data.txt` as data;

select cast(split(_c0," ")[0] as Long) as edgeSrc,cast(split(_c0," ")[1] as Long) as edgeDst from data
as new_data;

train new_data as PageRank.`/tmp/pr_model` ;
register PageRank.`/tmp/pr_model` as zhl_pr_redict;

select zhl_pr_redict(edgeSrc) from new_data;
```




### LogisticRegressor

```sql
load libsvm.`/tmp/lr.csv` options header="True" as lr;
train lr as LogisticRegressor.`/tmp/linear_regression_model`;
register LogisticRegressor.`/tmp/linear_regression_model` as lr_predict;
select lr_predict(features) from lr as result;
save overwrite result as json.`/tmp/lr_result.csv`;
```

### StandardScaler

```sql
load libsvm.`/Users/allwefantasy/Softwares/spark-2.2.0-bin-hadoop2.7/data/mllib/sample_libsvm_data.txt` as data;

train data as StandardScaler.`/tmp/kk`where inputCol="features";
register StandardScaler.`/tmp/kk` as predict;
select predict(features) as k from data;
```

### DicOrTableToArray

DicOrTableToArray 是一个很有意思的模型，很多场景我们需要把表或者字典转化为一个数组，然后在任何时候我都希望通过一个函数就获得这个数组，
那么这个模型可以提供这个功能。

示例：


有文件/tmp/test.txt文件如下：

```
content
a b c
b c d
e f g
```

有字典：/tmp/abc.txt:

```
a
b
c
```



```sql
load csv.`/tmp/test.txt` options header="True" as data; 

select split(content," ") as words from data 
as newdata;

train newdata as DicOrTableToArray.`/tmp/model` where 
`dic.paths`="/tmp/abc.txt" 
and `dic.names`="dic1";

register DicOrTableToArray.`/tmp/model` as p;
```

dic.paths 指定字典的路径，多个按','分割
dic.names 指定之巅的名称，多个按','分割

table.paths 指定表，多个按','分割
table.names 指定之巅的名称，多个按','分割

之后就可以注册模型了，假设该函数为p，调用该函数并且传入字典名称，你可以随时一个字符串数组，
并且通过array_slice,array_index等数组相关的函数进行操作。有的时候，我们希望对数组里的
元素进行一一解析，一个简单的技巧是：

我们先把数组转化为表：

```sql
select explode(k) as word from (select p("dic1") as k)
as words_table;

```

接着就可以对每个元素做处理了:

```sql
select lower(word) from words_table
as array_table;
```

处理完成后，我们希望能用相同的方式，比如调用一个函数，就能随时获取一个数组，这个时候我们复用下前面的训练：


```sql
train newdata as DicOrTableToArray.`/tmp/model2` where 
`table.paths`="array_table" 
and `table.names`="dic2";

register DicOrTableToArray.`/tmp/model2` as p2;
```

现在，我们可以通过调用p2获取处理后的数组了：

```
select p2("dic2")  as k
```