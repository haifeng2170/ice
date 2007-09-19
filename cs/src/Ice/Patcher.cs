// **********************************************************************
//
// Copyright (c) 2003-2007 ZeroC, Inc. All rights reserved.
//
// This copy of Ice is licensed to you under the terms described in the
// ICE_LICENSE file included in this distribution.
//
// **********************************************************************

using System;
using System.Collections.Generic;
using System.Reflection;
using System.Diagnostics;

namespace IceInternal
{
    public abstract class Patcher : Ice.ReadObjectCallback
    {
        public Patcher()
        {
        }

        public Patcher(Type type)
        {
            type_ = type;
        }

        public abstract void patch(Ice.Object v);

        public virtual string type()
        {
            Debug.Assert(type_ != null);
            return type_.FullName;
        }

        public virtual void invoke(Ice.Object v)
        {
            patch(v);
        }

        protected Type type_;
    }

    public sealed class ParamPatcher : Patcher
    {
        public ParamPatcher(Type type, string expectedSliceType) : base(type)
        {
            _expectedSliceType = expectedSliceType;
        }

        public override void patch(Ice.Object v)
        {
            Debug.Assert(type_ != null);
            if(v != null && !type_.IsInstanceOfType(v))
            {
                throw new Ice.UnexpectedObjectException(
                        "unexpected class instance of type `" + v.ice_id()
                            + "'; expected instance of type '" + _expectedSliceType + "'",
                        v.ice_id(), _expectedSliceType);
            }
            value = v;
        }

        public Ice.Object value;
        private string _expectedSliceType;
    }

    public sealed class CustomSeqPatcher<T> : Patcher
    {
        public CustomSeqPatcher(IEnumerable<T> seq, Type type, int index) : base(type)
        {
           _seq = seq;
           _index = index;

           setInvokeInfo(seq);
        }

        public override void patch(Ice.Object v)
        {
            Debug.Assert(type_ != null);
            if(v != null && !type_.IsInstanceOfType(v))
            {
                throw new InvalidCastException("expected element of type " + type() +
                                                      " but received " + v.GetType().FullName);
            }

            InvokeInfo info = getInvokeInfo(typeof(T));
            int count = info.getCount(_seq);
            if(_index >= count) // Need to grow the sequence.
            {
                for(int i = count; i < _index; i++)
                {
                    info.invokeAdd(_seq, default(T));
                }
                info.invokeAdd(_seq, (T)v);
            }
            else
            {
                info.invokeSet(_seq, _index, (T)v);
            }
        }

        private static InvokeInfo getInvokeInfo(Type t)
        {
            lock(_methodTable)
            {
                try
                {
                    return _methodTable[t];
                }
                catch(KeyNotFoundException)
                {
                    throw new Ice.MarshalException("No invoke record for type " + t.ToString());
                }
            }
        }

        private static void setInvokeInfo(IEnumerable<T> seq)
        {
            lock(_methodTable)
            {
                Type t = seq.GetType();
                if(_methodTable.ContainsKey(t))
                {
                    return;
                }

                MethodInfo am = t.GetMethod("Add", _params);
                if(am == null)
                {
                    throw new Ice.MarshalException("Cannot patch a collection without an Add() method");
                }

                PropertyInfo pi = t.GetProperty("item");
                if(pi == null)
                {
                    throw new Ice.MarshalException("Cannot patch a collection without an indexer");
                }
                MethodInfo sm = pi.GetSetMethod();
                if(sm == null)
                {
                    throw new Ice.MarshalException("Cannot patch a collection without an indexer to set a value");
                }

                pi = t.GetProperty("Count");
                if(pi == null)
                {
                    throw new Ice.MarshalException("Cannot patch a collection without a Count property");
                }
                MethodInfo cm = pi.GetGetMethod();
                if(cm == null)
                {
                    throw new Ice.MarshalException("Cannot patch a collection without a readable Count property");
                }

                _methodTable.Add(t, new InvokeInfo(am, sm, cm));
            }
        }

        private class InvokeInfo
        {
            public InvokeInfo(MethodInfo am, MethodInfo sm, MethodInfo cm)
            {
                _addMethod = am;
                _setMethod = sm;
                _countMethod = cm;
            }

            internal int getCount(System.Collections.IEnumerable seq)
            {
                try
                {
                    return (int)_countMethod.Invoke(seq, null);
                }
                catch(Exception ex)
                {
                    throw  new Ice.MarshalException("Could not read Count property during patching", ex);
                }
            }

            internal void invokeAdd(System.Collections.IEnumerable seq, T v)
            {
                try
                {
                    object[] arg = new object[] { v };
                    _addMethod.Invoke(seq, arg);
                }
                catch(Exception ex)
                {
                    throw  new Ice.MarshalException("Could not invoke Add method during patching", ex);
                }
            }

            internal void invokeSet(System.Collections.IEnumerable seq, int index, T v)
            {
                try
                {
                    object[] args = new object[] { index, v };
                    _setMethod.Invoke(seq, args);
                }
                catch(Exception ex)
                {
                    throw  new Ice.MarshalException("Could not call indexer during patching", ex);
                }
            }

            private MethodInfo _addMethod;
            private MethodInfo _setMethod;
            private MethodInfo _countMethod;
        }

        private static Type[] _params = new Type[] { typeof(T) };
        private static Dictionary<Type, InvokeInfo> _methodTable = new Dictionary<Type, InvokeInfo>();

        private IEnumerable<T> _seq;
        private int _index; // The index at which to patch the sequence.
    }

    public sealed class ArrayPatcher<T> : Patcher
    {
        public ArrayPatcher(T[] seq, Type type, int index) : base(type)
        {
            _seq = seq;
            _index = index;
        }

        public override void patch(Ice.Object v)
        {
            Debug.Assert(type_ != null);
            if(v != null && !type_.IsInstanceOfType(v))
            {
                throw new InvalidCastException("expected element of type " + type() +
                                                      " but received " + v.GetType().FullName);
            }

            _seq[_index] = (T)v;
        }

        private T[] _seq;
        private int _index; // The index at which to patch the array.
    }

    public sealed class SequencePatcher<T> : Patcher
    {
        public SequencePatcher(Ice.CollectionBase<T> seq, Type type, int index) : base(type)
        {
            _seq = seq;
            _index = index;
        }

        public override void patch(Ice.Object v)
        {
            Debug.Assert(type_ != null);
            if(v != null && !type_.IsInstanceOfType(v))
            {
                throw new InvalidCastException("expected element of type " + type() +
                                                      " but received " + v.GetType().FullName);
            }

            int count = _seq.Count;
            if(_index >= count) // Need to grow the sequence.
            {
                for(int i = count; i < _index; i++)
                {
                    _seq.Add(default(T));
                }
                _seq.Add((T)v);
            }
            else
            {
                _seq[_index] = (T)v;
            }
        }

        private Ice.CollectionBase<T> _seq;
        private int _index; // The index at which to patch the sequence.
    }

    public sealed class ListPatcher<T> : Patcher
    {
        public ListPatcher(List<T> seq, Type type, int index) : base(type)
        {
            _seq = seq;
            _index = index;
        }

        public override void patch(Ice.Object v)
        {
            Debug.Assert(type_ != null);
            if(v != null && !type_.IsInstanceOfType(v))
            {
                throw new InvalidCastException("expected element of type " + type() +
                                                      " but received " + v.GetType().FullName);
            }

            int count = _seq.Count;
            if(_index >= count) // Need to grow the sequence.
            {
                for(int i = count; i < _index; i++)
                {
                    _seq.Add(default(T));
                }
                _seq.Add((T)v);
            }
            else
            {
                _seq[_index] = (T)v;
            }
        }

        private List<T> _seq;
        private int _index; // The index at which to patch the sequence.
    }
}
