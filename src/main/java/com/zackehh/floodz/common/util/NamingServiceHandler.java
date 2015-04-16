package com.zackehh.floodz.common.util;

import com.zackehh.floodz.common.Constants;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;
import org.omg.PortableServer.Servant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A wrapping class to retrieve a NameService without duplicating code. It's a
 * little messy due to the need to provide a version specific to the calling class,
 * but it removes the need to change code in all component classes.
 */
public class NamingServiceHandler {

    /**
     * Start up a {@link org.slf4j.Logger} to output any information.
     */
    private static final Logger logger = LoggerFactory.getLogger(NamingServiceHandler.class);

    /**
     * Binds a CORBA Object reference to the NameService with a given name. Shorthand
     * wrapping to be able to access binding from outside the setup class.
     *
     * @param nameService the naming service
     * @param ref the object reference
     * @param name the name of the object
     */
    public static void bind(NamingContextExt nameService, org.omg.CORBA.Object ref, String name) throws Exception {
        nameService.rebind(nameService.to_name(name), ref);
    }

    /**
     * Creates a CORBA Object reference based on the passed in class, using
     * the helper associated with the class to create a reference. This is not
     * ideal, because it does include an element of risk with handling; however
     * because the components are pretty well defined at this point, it is unlikely
     * to change often.
     *
     * @param namingPair the naming pair
     * @param servant the Servant object
     * @param clazz the class of the helper to invoke on
     * @return an {@link org.omg.CORBA.Object} reference
     */
    public static org.omg.CORBA.Object createRef(NamingPair namingPair, Servant servant, Class<?> clazz) throws Exception {

        org.omg.CORBA.Object ref = namingPair.getRootPOA().servant_to_reference(servant);

        try {
            return org.omg.CORBA.Object.class.cast(
                    clazz.getMethod("narrow", org.omg.CORBA.Object.class).invoke(null, ref)
            );
        } catch(IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.debug("{}", e);
            throw new IllegalArgumentException("Unrecognised helper passed to #createRef: " + clazz.getCanonicalName());
        }

    }

    /**
     * Main handler for use in the Driver classes in order to retrieve a NameService and
     * register the Driver class as a CORBA Object reference. Binds the service before
     * returning the NameService to the calling context.
     *
     * @param orb the {@link org.omg.CORBA.ORB} instance
     * @param servant the Servant object
     * @param name the name of the object to register
     * @param clazz the class of the helper to use when creating a ref
     * @return a {@link org.omg.CosNaming.NamingContextExt} instance
     */
    public static NamingContextExt register(ORB orb, Servant servant, String name, Class clazz) throws Exception {

        // retrieve a NamingPair
        NamingPair namingPair = retrieveNameService(orb);

        // check for null just in case
        if(namingPair == null){
            return null;
        }

        // grab the NameService
        NamingContextExt namingService = namingPair.getNamingService();

        // create an object reference from the servant
        org.omg.CORBA.Object server_ref = createRef(namingPair, servant, clazz);

        // bind the reference to the NameService
        bind(namingPair.getNamingService(), server_ref, name);

        // Return the NameService
        return namingService;

    }

    /**
     * Retrieves the NameService using the provided {@link org.omg.CORBA.ORB}, and returns
     * a pair of {@link org.omg.CosNaming.NamingContextExt} and {@link org.omg.PortableServer.POA}.
     *
     * @param orb the {@link org.omg.CORBA.ORB} to use
     * @return a {@link NamingPair} instance
     */
    public static NamingPair retrieveNameService(ORB orb) throws Exception {

        // get a reference to the RootPOA, and active the manager
        POA rootpoa = POAHelper.narrow(orb.resolve_initial_references(Constants.ROOT_POA));
        if (rootpoa != null) {
            rootpoa.the_POAManager().activate();
        } else {
            logger.error("Unable to retrieve POA!");
            return null;
        }

        // get a reference to the NameService and check null
        org.omg.CORBA.Object namingServiceObj = orb.resolve_initial_references(Constants.NAME_SERVICE);
        if (namingServiceObj == null) {
            return null;
        }

        // Return the NameService and RootPOA inside of a NamingPair
        return new NamingPair(NamingContextExtHelper.narrow(namingServiceObj), rootpoa);
    }

    public static <T> T retrieveObject(NamingContextExt nameService, String name, Class<T> clazz, Class<?> helperClazz) {
        try {
            Method method = helperClazz.getMethod("narrow", org.omg.CORBA.Object.class);
            return clazz.cast(method.invoke(null, nameService.resolve_str(name)));
        } catch(Exception e) {
            return null;
        }
    }

}
