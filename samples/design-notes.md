# Software Design Notes

Encapsulation keeps an object's internal state private and exposes operations through methods.
An interface describes behavior that different classes can implement.
Inheritance allows a subclass to reuse and specialize superclass behavior.

The Observer pattern connects one subject to several observers.
The subject keeps a list of observers and notifies them when its state changes.
An observer receives an update and reads the current state from the subject.

The Strategy pattern separates an algorithm from the class that uses it.
A context holds a strategy and delegates an operation to it.
Changing the strategy changes the algorithm without changing the context.

A facade provides a simple entry point to several related services.
An adapter translates an expected interface into another interface.
Factory Method lets subclasses decide which concrete product to create.
