#pragma once

template <typename A> class Col {
  public:
    ColBuilder builder();
    Array<A> arr();
    int32_t length();
    A apply(int32_t i);
    template <typename B> Col<B> map(std::function<B(A)> f);
    template <typename B> PairCol<A, B> zip(const Col<B>& ys);
};

template <typename L, typename R> class PairCol : public Col<std::tuple<L, R>> {
  public:
    Col<L> ls();
    Col<R> rs();
};
class ColBuilder {
  public:
    template <typename A, typename B>
    PairCol<A, B> apply(const Col<A>& as, const Col<B>& bs);
    template <typename T> Col<T> fromArray(const Array<T>& arr);
    template <typename T> Col<T> replicate(int32_t n, const T& v);
    int32_t ddmvm(const Array<double>& v);
    Array<double> functorArg(const Array<double>& arr,
                             const Functor<Array>& evF);
    Value arrayMut(Array<double>& arr);
};

template <template F> class Functor {
  public:
    template <typename A, typename B>
    F<B> map(const F<A>& fa, std::function<B(A)> f, const ClassTag<B>& tB);
};
class Enum {
  public:
    int32_t value();
};

template <typename A> class Col {
  public:
    ColBuilder builder();
    Array<A> arr();
    int32_t length();
    A apply(int32_t i);
    template <typename B> Col<B> map(std::function<B(A)> f);
    template <typename B> PairCol<A, B> zip(const Col<B>& ys);
};

template <typename L, typename R> class PairCol : public Col<std::tuple<L, R>> {
  public:
    Col<L> ls();
    Col<R> rs();
};
class ColBuilder {
  public:
    template <typename A, typename B>
    PairCol<A, B> apply(const Col<A>& as, const Col<B>& bs);
    template <typename T> Col<T> fromArray(const Array<T>& arr);
    template <typename T> Col<T> replicate(int32_t n, const T& v);
    int32_t ddmvm(const Array<double>& v);
    Array<double> functorArg(const Array<double>& arr,
                             const Functor<Array>& evF);
    Value arrayMut(Array<double>& arr);
};

template <template F> class Functor {
  public:
    template <typename A, typename B>
    F<B> map(const F<A>& fa, std::function<B(A)> f, const ClassTag<B>& tB);
};
class Enum {
  public:
    int32_t value();
};
