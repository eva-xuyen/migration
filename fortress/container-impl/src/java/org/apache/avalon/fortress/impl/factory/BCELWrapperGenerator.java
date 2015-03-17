/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 * 
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.avalon.fortress.impl.factory;

import java.security.ProtectionDomain;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.Type;
import org.apache.bcel.util.ClassLoaderRepository;
import org.apache.bcel.util.Repository;

/**
 * Create the BCELWrapper for the component.
 * The generated wrapper classes will be assigned the same ProtectionDomain as
 *  the actual classes which they are wrapping.  This simplifies the
 *  configuration of a SecurityManager by making the existence of the BCEL
 *  generated classes transparent to the policy file author.
 *
 * @author <a href="mailto:dev@avalon.apache.org">Avalon Development Team</a>
 */
final class BCELWrapperGenerator
{
    /**
     * The BCEL util.Repository instance to use when loading JavaClass instances.
     * Default is to use util.ClassLoaderRepository with thread context classloader.
     */
    private Repository m_repository = null;

    /**
     * The suffix to be appended to the name of the wrapped class when creating
     * the name of the wrapper class.
     */
    private static final String WRAPPER_CLASS_SUFFIX = "$BCELWrapper";

    /**
     * The name of the superclass of the wrapper class to be generated.
     */
    private static final String WRAPPER_SUPERCLASS_NAME = "org.apache.avalon.fortress.impl.handler.AbstractReleasableComponent";

    /**
     * The name of the interface each generated wrapper class has to implement.
     */
    private static final String WRAPPER_CLASS_INTERFACE_NAME =
        WrapperClass.class.getName();

    /**
     * The <code>BCELCodeGenerator</code> to use for
     * byte code generation.
     */
    private final BCELCodeGenerator m_codeGenerator;

    /**
     * The <code>ClassGen</code> instance to use for byte code generation.
     */
    private ClassGen m_classGenerator = null;

    /**
     * The <code>ClassLoader</code> to use when loading a class generated by this
     * <code>BCELWrapperGenerator</code>.
     */
    private final BCELClassLoader m_bcelClassLoader;

    /**
     * @author <a href="mailto:dev@avalon.apache.org">Avalon Development Team</a>
     */
    private final class BCELClassLoader extends ClassLoader
    {
        /**
         * The <i>byte code</i> representing the wrapper class created by the
         * enclosing <code>BCELWrapperGenerated</code>. This field will be
         * managed by the <code>BCELWrapperGenerator</code>.
         */
        private byte[] m_byteCode = null;
        
        /**
         * The ProtectionDomain to use for the newly generated class.  When a
         *  SecurityManager is set, this will determine what privileges this
         *  class will have.
         */
        private ProtectionDomain m_protectionDomain;

        /**
         * Constructs a <code>BCELClassLoader</code> with the specified class
         * loader as its parent.
         *
         * @param parent The parent <code>ClassLoader</code>
         */
        public BCELClassLoader( final ClassLoader parent )
        {
            super( parent );
        }

        /**
         * Constructs a <code>BCELClassLoader</code> with no parent.
         *
         */
        public BCELClassLoader()
        {
            super();
        }

        /**
         *
         * @see java.lang.ClassLoader#findClass(String)
         */
        protected Class findClass( final String name ) throws ClassNotFoundException
        {
            // Check if the requested class falls within the domain of
            // the BCELWrapperGenerator
            if ( name.endsWith( WRAPPER_CLASS_SUFFIX ) )
            {
                Class clazz = super.defineClass(
                    name,
                    m_byteCode,
                    0,
                    m_byteCode.length,
                    m_protectionDomain );
                
                return clazz;
            }

            return super.findClass( name );
        }

        /**
         * Passes in data needed to create and initialze the new class when
         *  findClass is called by the <code>BCELWrapperGenerator</code>.
         * This method will be called by the <code>BCELWrapperGenerator</code>
         * prior to asking this class loader for the generated wrapper class.
         *
         * @param byteCode The <code>byte code</code> to use when loading
         *                  the generated class
         * @param protectionDomain The ProtectionDomain to use when loading
         *                         the generated class.
         *
         * @throws IllegalArgumentException If <code>byteCode</code> is null or
         *          empty
         */
        private void setup( final byte[] byteCode, final ProtectionDomain protectionDomain )
            throws IllegalArgumentException
        {
            if ( byteCode == null || byteCode.length == 0 )
            {
                final String message =
                    "Parameter byteCode must neither be <null> nor empty.";
                throw new IllegalArgumentException( message );
            }

            m_byteCode = byteCode;
            m_protectionDomain = protectionDomain;
        }

        /**
         * Clears the data used to generate a class to free up memory.
         * This method will be called by the <code>BCELWrapperGenerator</code>
         * immediately after this class loader has returned the generated wrapper
         * class.
         */
        private void tearDown()
        {
            m_byteCode = null;
            m_protectionDomain = null;
        }

        /**
         * Returns the <code>byte code</code> to use when loading a generated
         * class.
         *
         * @return The <code>byte code</code> for defining the generated class
         */
        private byte[] getByteCode()
        {
            return m_byteCode;
        }
    } // End BCELClassLoader

    /**
     * No-args default constructor.
     */
    public BCELWrapperGenerator()
    {
        m_codeGenerator = new BCELCodeGenerator();
        ClassLoader contextClassLoader =
                Thread.currentThread().getContextClassLoader();
        m_repository = new ClassLoaderRepository( contextClassLoader );
        m_bcelClassLoader =
            new BCELClassLoader( contextClassLoader );
    }

    /**
     */
    public synchronized Class createWrapper( final Class classToWrap ) throws Exception
    {
        if ( classToWrap == null )
        {
            final String message = "Class to wrap must not be <null>.";
            throw new IllegalArgumentException( message );
        }
        
        // Use the same ProtectionDomain as the class being wrapped.
        ProtectionDomain protectionDomain = classToWrap.getProtectionDomain();

        // Guess work interfaces ...
        final Class[] interfacesToImplement =
            AbstractObjectFactory.guessWorkInterfaces( classToWrap );

        // Get JavaClasses as required by BCEL for the wrapped class and its interfaces
        final JavaClass javaClassToWrap = lookupClass( classToWrap );
        final JavaClass[] javaInterfacesToImplement =
            lookupClasses( interfacesToImplement );

        // The name of the wrapper class to be generated
        final String wrapperClassName =
            classToWrap.getName() + WRAPPER_CLASS_SUFFIX;

        Class generatedClass;
        synchronized ( Type.class )
        {
            // Create BCEL class generator
            m_classGenerator =
                new ClassGen(
                    wrapperClassName,
                    WRAPPER_SUPERCLASS_NAME,
                    null,
                    Constants.ACC_FINAL
                |Constants.ACC_PUBLIC
                |Constants.ACC_SUPER,
                    extractInterfaceNames( interfacesToImplement ) );

            // Initialize method-field generator
            m_codeGenerator.init(
                wrapperClassName,
                WRAPPER_SUPERCLASS_NAME,
                javaClassToWrap,
                m_classGenerator );

            final byte[] byteCode = buildWrapper( javaInterfacesToImplement );
            m_bcelClassLoader.setup( byteCode, protectionDomain );
            try
            {
                generatedClass = m_bcelClassLoader.loadClass( wrapperClassName );
            }
            finally
            {
                m_bcelClassLoader.tearDown();
            }
        }

        return generatedClass;
    }

    /**
     * Takes a <code>Class</code> instance as a its parameter and returns corresponding
     * the <code>JavaClass</code> instance as used by <b>BCEL</b>.
     *
     * @param clazz The <code>Class</code> instance we want to turn into a
     *               <code>JavaClass</code>
     * @return The <code>JavaClass</code> representing the given <code>Class</code>
     *          instance
     */
    private JavaClass lookupClass( final Class clazz ) throws Exception
    {
        String className = clazz.getName();
        try
        {
            JavaClass jClazz = m_repository.findClass( className );
            if ( jClazz == null )
            return m_repository.loadClass( className );
            else
            return jClazz;
        } catch ( ClassNotFoundException e ) { return null; }
    }

    /**
     * Takes an array of <code>Class</code> instances and returns an array holding
     * the corresponding <code>JavaClass</code> instances as used by <b>BCEL</b>.
     *
     * @param classes      An array holding <code>Class</code> instances we
     *                      want to turn into <code>JavaClass</code> instances
     * @return JavaClass[] An array of <code>JavaClass</code> instances representing
     *                      the given <code>Class</code> instances
     */
    private JavaClass[] lookupClasses( final Class[] classes ) throws Exception
    {
        final JavaClass[] javaClasses = new JavaClass[classes.length];
        for ( int i = 0; i < classes.length; ++i )
        {
            javaClasses[i] = lookupClass( classes[i] );
        }

        return javaClasses;
    }

    /**
     * Takes an array of <code>Class</code> instances supposed to represent
     * interfaces and returns a list of the names of those interfaces.
     *
     * @param interfaces An array of <code>Class</code> instances
     * @return String[]  An array of the names of those <code>Class</code> instances
     */
    private String[] extractInterfaceNames( final Class[] interfaces )
    {
        final String[] ifaceNames = new String[interfaces.length + 1];
        for ( int i = 0; i < interfaces.length; ++i )
        {
            ifaceNames[i] = interfaces[i].getName();
        }
        // Add interface WrapperClass to the list of interfaces to be implemented
        ifaceNames[ifaceNames.length - 1] = WRAPPER_CLASS_INTERFACE_NAME;

        return ifaceNames;
    }

    /**
     * Generates the wrapper byte code for a given interface.
     *
     * @param interfacesToImplement The interfaces we want to generate wrapper
     *                               byte code for
     * @return byte[] The generated byte code
     */
    private byte[] buildWrapper( final JavaClass[] interfacesToImplement ) throws Exception
    {
        // Create field for the wrapped class
        m_classGenerator.addField( m_codeGenerator.createWrappedClassField() );

        // Create default constructor
        m_classGenerator.addMethod( m_codeGenerator.createDefaultConstructor() );

        //Create field accessor for wrapped class instance
        m_classGenerator.addMethod(
            m_codeGenerator.createWrappedClassAccessor() );

        // Implement interfaces
        Method[] interfaceMethods = m_codeGenerator.createImplementation( interfacesToImplement );

        for ( int j = 0; j < interfaceMethods.length; ++j )
        {
            m_classGenerator.addMethod( interfaceMethods[j] );
        }

        return m_classGenerator.getJavaClass().getBytes();
    }
}