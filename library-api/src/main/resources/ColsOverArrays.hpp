
template <typename A> class ColOverArray : public Col<A> {
  public:
    ColOverArrayBuilder builder();
    int32_t length();
    A apply(int32_t i);
    template <typename B> Col<B> map(std::function<B(A)> f);
};

template <typename L, typename R> class PairOfCols : public PairCol<L, R> {
  public:
    ColBuilder builder();
    Array<std::tuple<L, R>> arr();
    int32_t length();
    std::tuple<L, R> apply(int32_t i);
    template <typename V> Col<V> map(std::function<V(std::tuple<L, R>)> f);
};
class ColOverArrayBuilder : public ColBuilder {
  public:
    template <typename A, typename B>
    PairCol<A, B> apply(const Col<A>& as, const Col<B>& bs);
    template <typename T> Col<T> fromArray(const Array<T>& arr);
};
class ArrayFunctor : public Functor<Array> {
  public:
    template <typename A, typename B>
    Array<B> map(const Array<A>& fa, std::function<B(A)> f,
                 const ClassTag<B>& tB);
};
