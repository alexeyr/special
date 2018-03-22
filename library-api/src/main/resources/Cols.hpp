#pragma once

template <class A> class Col;
template <class L, class R> class PairCol;
class ColBuilder;
template <template <class> class F> class Functor;
class Enum;
template <class A> class Col {
  public:
    ColBuilder builder();
    A[] arr();
    int32_t length();
    A apply(int32_t i);
    template <class B> Col<B> map(std::function<B(A)> f);
    template <class B> PairCol<A, B> zip(const Col<B>& ys);
};
template <class L, class R> class PairCol : public Col<std::tuple<L, R>> {
  public:
    Col<L> ls();
    Col<R> rs();
};
class ColBuilder {
  public:
    template <class A, class B>
    PairCol<A, B> apply(const Col<A>& as, const Col<B>& bs);
    template <class T> Col<T> fromArray(T[] arr);
    template <class T> Col<T> replicate(int32_t n, const T& v);
    int ddmvm(double[] v, int32_t& result);
    double[] functorArg(double[] arr, const Functor<Array>& evF);
    Value arrayMut(double[] arr);
};
template <template <class> class F> class Functor {
  public:
    template <class A, class B>
    F<B> map(const F<A>& fa, std::function<B(A)> f, const ClassTag<B>& tB);
};
class Enum {
  public:
    int32_t value();
};
