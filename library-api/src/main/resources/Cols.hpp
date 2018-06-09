#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <tuple>

namespace scalan {
namespace collection {
class Array;                          // don't know from where!
template <class A> class RepeatedArg; // don't know from where!
class Value;                          // don't know from where!

template <class A> class Col;
template <class L, class R> class PairCol;
class ColBuilder;
template <template <class> class F> class Functor;
class Enum;
template <class A> class Col {
  public:
    Col() = default;
    std::shared_ptr<scalan::collection::ColBuilder> builder() const;
    A* arr() const;
    int32_t length() const;
    std::shared_ptr<A> apply(int32_t i) const;
    template <class B>
    std::shared_ptr<scalan::collection::Col<B>>
    map(std::function<B(A)> f) const;
    template <class B>
    std::shared_ptr<scalan::collection::PairCol<A, B>>
    zip(std::shared_ptr<const scalan::collection::Col<B>> const& ys) const {
        return this->builder()->apply<A, B>(*this, ys);
    }

    void foreach (std::function<void(A)> f) const;
    bool exists(std::function<bool(A)> p) const;
};
template <class A> Col<A> make_Col() { return Col<A>(); }

template <class L, class R>
class PairCol : public scalan::collection::Col<std::tuple<L, R>> {
  public:
    PairCol() = default;
    std::shared_ptr<scalan::collection::Col<L>> ls() const;
    std::shared_ptr<scalan::collection::Col<R>> rs() const;
};
template <class L, class R> PairCol<L, R> make_PairCol() {
    return PairCol<L, R>();
}

class ColBuilder {
  public:
    ColBuilder() = default;
    template <class A, class B>
    std::shared_ptr<scalan::collection::PairCol<A, B>>
    apply(std::shared_ptr<const scalan::collection::Col<A>> const& as,
          std::shared_ptr<const scalan::collection::Col<B>> const& bs) const;
    template <class T>
    std::shared_ptr<scalan::collection::Col<T>>
    apply(std::shared_ptr<const RepeatedArg<T>> const& items) const;
    std::shared_ptr<scalan::collection::Col<int32_t>> fromItemsTest() const;
    template <class T>
    std::shared_ptr<scalan::collection::Col<T>> fromArray(T* arr) const;
    template <class T>
    std::shared_ptr<scalan::collection::Col<T>>
    replicate(int32_t n, std::shared_ptr<const T> const& v) const;
    template <class T>
    std::shared_ptr<T>
    dot(std::shared_ptr<const scalan::collection::Col<T>> const& xs,
        std::shared_ptr<const scalan::collection::Col<T>> const& ys) const;
    int32_t ddmvm(double* v, int32_t& result) const;
    double*
    functorArg(double* arr,
               std::shared_ptr<const scalan::collection::Functor<Array>> const&
                   evF) const;
    std::shared_ptr<Value> arrayMut(double* arr) const;
    int32_t throwing(int32_t& result) const;
    template <class A>
    int32_t
    throwing2(std::shared_ptr<scalan::collection::Col<A>>& result) const {
        throw "Unimplemented";
    }
};
template <template <class> class F> class Functor {
  public:
    Functor() = default;
    template <class A, class B>
    std::shared_ptr<F<B>> map(std::shared_ptr<const F<A>> const& fa,
                              std::function<B(A)> f) const;
};
template <template <class> class F> Functor<F> make_Functor() {
    return Functor<F>();
}

class Enum {
  public:
    Enum() = default;
    int32_t value() const;
};
} // namespace collection
} // namespace scalan
