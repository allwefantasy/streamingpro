# 使用Python处理MLSQL中的表

在前面章节，我们提到了可以交互式使用python。但是因为他本身不是分布式的，所以性能会比较差。我们还提供了专门使用Python处理MLSQL中表的能力。

> 使用前，请先在环境中安装pyjava. 尝试使用 pip install pyjava命令。
> pyjava会提供一个叫data_manager的变量，方便接受和返回数据给MLSQL主程序。
> 主要有两个方法：
>    获取数据， data_manager.fetch_once(), 返回一个迭代器，注意，该方法只能调用一次。
>    设置返回数据， data_manager.set_output(value) value格式必须是 [[pandas.serial,pandas.serial,...]]

第一步，我们模拟一张表：

```sql
select 1 as a as table1;
```

第二步，设置环境以及Python处理后返回的表格式：

```sql
!python env "PYTHON_ENV=export PYTHONIOENCODING=utf8 && source activate streamingpro-spark-2.4.x";
!python conf "schema=st(field(id,long),field(label,double))";
```
在上面的的代码中，我选择使用一个叫`streamingpro-spark-2.4.x`的conda虚拟环境。如果不使用虚拟环境，则可设置为`:`,这样：

```sql
!python env "PYTHON_ENV=:";
```

第三步，书写Python代码：

```sql
!python on orginal_text_corpus1 '''

import pandas as pd
import numpy as np

def process(_data_manager):
    for item in _data_manager.fetch_once_as_rows():
        item["label"] = item["label"] + 1
        del item["content"]
        yield item


items = process(data_manager)

data_manager.build_result(items, 1024)

''' named mlsql_temp_table2;
```

这里，表示对orginal_text_corpus1使用python进行处理，处理后，我们通过data_manager.build_result方法设置返回值，返回值需要符合我们前面定义的Schema. 
最后结果我们取名为 mlsql_temp_table2。


现在，你可以获取结果数据看看：

```sql
select * from mlsql_temp_table2 as output;
```

下面是一个完整的示例：

```sql
set rawText='''
{"id":9,"content":"1","label":0.0}
{"id":10,"content":"2","label":0.0}
{"id":11,"content":"中国","label":0.0}
{"id":12,"content":"e","label":0.0}
{"id":13,"content":"5","label":0.0}
{"id":14,"content":"4","label":0.0}
''';

load jsonStr.`rawText` as orginal_text_corpus;

select id,content,label from orginal_text_corpus as orginal_text_corpus1;

!python env "PYTHON_ENV=export PYTHONIOENCODING=utf8 && source activate streamingpro-spark-2.4.x";
!python conf "schema=st(field(id,long),field(label,double))";

!python on orginal_text_corpus1 '''

import pandas as pd
import numpy as np

def process(_data_manager):
    for item in _data_manager.fetch_once_as_rows():
        item["label"] = item["label"] + 1
        del item["content"]
        yield item


items = process(data_manager)

data_manager.build_result(items, 1024)

''' named mlsql_temp_table2;

select * from mlsql_temp_table2 as output;
```

值得注意的是：

1. 我们可以通过data_manager.fetch_once_as_rows 获取table1里的数据，通过data_manager.build_result 返回数据。
这期间会有多个python进程，每个python进程只处理其中一部分数据。也就是数据其实是分布式处理的，大家不要以为是在一个进程里。
2. 很多莫名其妙的错误都是因为schema描述错误，大家需要谨慎。 

## 关于使用Python处理MLSQL表的一些原理

当我们使用Python处理一张表的数据时，我们会在表分区的节点启动相应的Python Workers,并且我们会一直复用他们。
对于这些Python Workers，我们可以通过如下方式限制Worker的大小。

```sql
!python conf "py_executor_memory=600";
```

上面的例子表示我们会限制Python的worker内存大小不超过600m.
值得注意的是，无论`!python env`还是`!python conf`都是session级别有效的。这意味着一旦设置之后，会影响当前用户后续所有的操作。
