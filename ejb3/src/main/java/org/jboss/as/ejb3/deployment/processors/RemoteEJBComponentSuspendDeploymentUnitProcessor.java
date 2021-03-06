package org.jboss.as.ejb3.deployment.processors;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.Component;
import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.ComponentConfigurator;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.DependencyConfigurator;
import org.jboss.as.ee.component.ViewConfiguration;
import org.jboss.as.ee.component.interceptors.InterceptorOrder;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.component.EJBViewConfiguration;
import org.jboss.as.ejb3.component.MethodIntf;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.invocation.ImmediateInterceptorFactory;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.wildfly.extension.requestcontroller.ControlPoint;
import org.wildfly.extension.requestcontroller.ControlPointService;
import org.wildfly.extension.requestcontroller.RequestControllerActivationMarker;


/**
 * @author Stuart Douglas
 */
public class RemoteEJBComponentSuspendDeploymentUnitProcessor implements DeploymentUnitProcessor {

    public static final String ENTRY_POINT_NAME = "remote-ejb";

    @Override
    public void deploy(DeploymentPhaseContext context) {
        final DeploymentUnit deploymentUnit = context.getDeploymentUnit();
        final String topLevelName;
        //check if the controller is installed
        if(!RequestControllerActivationMarker.isRequestControllerEnabled(deploymentUnit)) {
            return;
        }
        if(deploymentUnit.getParent() == null) {
            ControlPointService.install(context.getServiceTarget(), deploymentUnit.getName(), ENTRY_POINT_NAME);
            topLevelName = deploymentUnit.getName();
        } else {
            topLevelName = deploymentUnit.getParent().getName();
        }
        for(ComponentDescription component : deploymentUnit.getAttachment(Attachments.EE_MODULE_DESCRIPTION).getComponentDescriptions()) {
            if(component instanceof EJBComponentDescription) {
                component.getConfigurators().add(new ComponentConfigurator() {
                    @Override
                    public void configure(DeploymentPhaseContext context, ComponentDescription description, ComponentConfiguration configuration) throws DeploymentUnitProcessingException {

                        EjbRemoteSuspendInterceptor interceptor = null;
                        ImmediateInterceptorFactory factory = null;
                        for(ViewConfiguration view: configuration.getViews()) {
                            EJBViewConfiguration ejbView = (EJBViewConfiguration) view;
                            if(ejbView.getMethodIntf() == MethodIntf.REMOTE ||
                                    ejbView.getMethodIntf() == MethodIntf.HOME) {
                                if(factory == null) {
                                    interceptor = new EjbRemoteSuspendInterceptor();
                                    factory = new ImmediateInterceptorFactory(interceptor);
                                }
                                view.addViewInterceptor(factory, InterceptorOrder.View.SHUTDOWN_INTERCEPTOR);
                            }
                        }
                        if(interceptor != null) {
                            final EjbRemoteSuspendInterceptor finalIntercepor = interceptor;
                            configuration.getCreateDependencies().add(new DependencyConfigurator<Service<Component>>() {
                                @Override
                                public void configureDependency(ServiceBuilder<?> serviceBuilder, Service<Component> service) throws DeploymentUnitProcessingException {
                                    serviceBuilder.addDependency(ControlPointService.serviceName(topLevelName, ENTRY_POINT_NAME), ControlPoint.class, finalIntercepor.getControlPointInjectedValue());
                                }
                            });

                        }

                    }
                });
            }
        }

    }

    @Override
    public void undeploy(DeploymentUnit context) {
        // Do nothing
    }
}
