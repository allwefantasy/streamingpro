import mlsql_model
import mlsql
from sklearn.naive_bayes import MultinomialNB


clf = MultinomialNB()

mlsql.sklearn_configure_params(clf)


def train(X, y, label_size):
    clf.partial_fit(X, y, classes=range(label_size))


mlsql.sklearn_batch_data(train)

mlsql_model.sk_save_model( clf)
