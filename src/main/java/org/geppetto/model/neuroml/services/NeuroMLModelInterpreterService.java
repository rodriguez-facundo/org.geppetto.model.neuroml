/*******************************************************************************
. * The MIT License (MIT)
 *
 * Copyright (c) 2011 - 2015 OpenWorm.
 * http://openworm.org
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the MIT License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/MIT
 *
 * Contributors:
 *     	OpenWorm - http://openworm.org/people.html
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/

package org.geppetto.model.neuroml.services;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geppetto.core.beans.ModelInterpreterConfig;
import org.geppetto.core.beans.PathConfiguration;
import org.geppetto.core.conversion.ConversionException;
import org.geppetto.core.data.model.IAspectConfiguration;
import org.geppetto.core.manager.Scope;
import org.geppetto.core.model.AModelInterpreter;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.ModelInterpreterException;
import org.geppetto.core.services.registry.ServicesRegistry;
import org.geppetto.model.DomainModel;
import org.geppetto.model.ExternalDomainModel;
import org.geppetto.model.GeppettoFactory;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.ModelFormat;
import org.geppetto.model.neuroml.features.LEMSParametersFeature;
import org.geppetto.model.neuroml.utils.OptimizedLEMSReader;
import org.geppetto.model.neuroml.utils.Resources;
import org.geppetto.model.neuroml.utils.ResourcesDomainType;
import org.geppetto.model.neuroml.utils.modeltree.PopulateSummaryNodesModelTreeUtils;
import org.geppetto.model.neuroml.visitors.ExtractVisualType;
import org.geppetto.model.types.ArrayType;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.CompositeVisualType;
import org.geppetto.model.types.ConnectionType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.types.VisualType;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.util.PointerUtility;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.Connection;
import org.geppetto.model.values.Connectivity;
import org.geppetto.model.values.Point;
import org.geppetto.model.values.Pointer;
import org.geppetto.model.values.Sphere;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;
import org.lemsml.jlems.api.LEMSDocumentReader;
import org.lemsml.jlems.api.interfaces.ILEMSDocument;
import org.lemsml.jlems.api.interfaces.ILEMSDocumentReader;
import org.lemsml.jlems.core.sim.ContentError;
import org.lemsml.jlems.core.sim.LEMSException;
import org.lemsml.jlems.core.type.Attribute;
import org.lemsml.jlems.core.type.Component;
import org.lemsml.jlems.core.type.Exposure;
import org.lemsml.jlems.core.type.Lems;
import org.lemsml.jlems.core.type.ParamValue;
import org.lemsml.jlems.io.xmlio.XMLSerializer;
import org.neuroml.export.utils.Utils;
import org.neuroml.model.NeuroMLDocument;
import org.neuroml.model.util.NeuroMLConverter;
import org.neuroml.model.util.NeuroMLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author matteocantarelli
 * @author Adrian Quintana (adrian.perez@ucl.ac.uk)
 * 
 */
@Service
public class NeuroMLModelInterpreterService extends AModelInterpreter
{
	private static Log _logger = LogFactory.getLog(NeuroMLModelInterpreterService.class);

	@Autowired
	private ModelInterpreterConfig neuroMLModelInterpreterConfig;

	private Map<String, Type> types = new HashMap<String, Type>();
	private Type type = null;

	private GeppettoModelAccess access;

	private TypesFactory typeFactory = TypesFactory.eINSTANCE;
	private ValuesFactory valuesFactory = ValuesFactory.eINSTANCE;
	private VariablesFactory variablesFactory = VariablesFactory.eINSTANCE;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.geppetto.core.model.IModelInterpreter#importType(java.net.URL, java.lang.String, org.geppetto.core.library.LibraryManager)
	 */
	@Override
	public Type importType(URL url, String typeId, GeppettoLibrary library, GeppettoModelAccess access) throws ModelInterpreterException
	{

		// AQP: Shall we verify if types != null?
		long startTime = System.currentTimeMillis();

		this.access = access;

		dependentModels.clear();

		try
		{
			OptimizedLEMSReader reader = new OptimizedLEMSReader(this.dependentModels);
			int index = url.toString().lastIndexOf('/');
			String urlBase = url.toString().substring(0, index + 1);
			reader.read(url, urlBase, OptimizedLEMSReader.NMLDOCTYPE.NEUROML); // expand it to have all the inclusions

			/*
			 * LEMS
			 */
			long start = System.currentTimeMillis();
			ILEMSDocumentReader lemsReader = new LEMSDocumentReader();
			ILEMSDocument lemsDocument = lemsReader.readModel(reader.getLEMSString());
			_logger.info("Parsed LEMS document, took " + (System.currentTimeMillis() - start) + "ms");

			/*
			 * NEUROML
			 */
			start = System.currentTimeMillis();
			NeuroMLConverter neuromlConverter = new NeuroMLConverter();
			NeuroMLDocument neuroml = neuromlConverter.loadNeuroML(reader.getNeuroMLString());
			_logger.info("Parsed NeuroML document of size " + reader.getNeuroMLString().length() / 1024 + "KB, took " + (System.currentTimeMillis() - start) + "ms");

			// Resolve lems model
			// If there is any problem resolving the lems model we will try to go on anyway as there are some models, as purkinje, which are not valid lems format
			Lems lems = ((Lems) lemsDocument);
			try
			{
				lems.setResolveModeLoose();
				lems.deduplicate();
				lems.resolve();
				lems.evaluateStatic();
			}
			catch(NumberFormatException | LEMSException e)
			{
				_logger.warn("Error resolving lems file");
			}

			// If we have a typeId let's get the type for this component
			// Otherwise let's iterate through all the components
			if(typeId != null && !typeId.isEmpty())
			{
				// type = extractInfoFromComponent(lems.getComponent(typeId));
				types.put(typeId, extractInfoFromComponent(lems.getComponent(typeId)));
				type = types.get(typeId);
			}
			else
			{
				boolean multipleTypes = false;
				for(Component component : lems.getComponents())
				{
					if(!types.containsKey(component.getID()))
					{

						types.put(component.getID(), extractInfoFromComponent(component));

						// Business rule: 1) If there is a network in the NeuroML file we don't visualise spurious cells which
						// "most likely" are just included types in NeuroML and are instantiated as part of the network
						// populations
						// If there is not a network we visualise the cell (as far as there is just a single cell)
						// If there is just a single component we return the single cell
						// Otherwise we throw an exception
						Type currentType = types.get(component.getID());
						if(type == null)
						{
							type = currentType;
						}
						else
						{
							String declaredType = ((Component) type.getDomainModel().getDomainModel()).getDeclaredType();
							String currentDeclaredType = ((Component) currentType.getDomainModel().getDomainModel()).getDeclaredType();
							if(!declaredType.equals("network") && (currentDeclaredType.equals("network") || (!declaredType.equals("cell") && currentDeclaredType.equals("cell"))))
							{
								multipleTypes = false;
								type = currentType;
							}
							else if((!declaredType.equals("cell") && !declaredType.equals("network")) || (declaredType.equals("cell") && currentDeclaredType.equals("cell"))
									|| (declaredType.equals("network") && currentDeclaredType.equals("network")))
							{
								multipleTypes = true;
							}
						}
					}
				}

				if(multipleTypes) throw new ModelInterpreterException("Multiple types found and no type id specified");
			}

			// Add all the types to the library
			library.getTypes().addAll(types.values());

			// Extract Summary and Description nodes from type
			//AQP we need to implement a map resoucesdomaintype-list<types> and a method for setting the domain type. Once than remove empty hashmap parameter
			PopulateSummaryNodesModelTreeUtils populateSummaryNodesModelTreeUtils = new PopulateSummaryNodesModelTreeUtils(neuroml, new HashMap<ResourcesDomainType, List<Type>>(), url, access);
			((CompositeType) type).getVariables().addAll(populateSummaryNodesModelTreeUtils.getSummaryVariables());

			// Add LEMS Parameter Feature
			this.addFeature(new LEMSParametersFeature(library));
		}
		catch(IOException | NumberFormatException | NeuroMLException | LEMSException | GeppettoVisitingException e)
		{
			throw new ModelInterpreterException(e);
		}

		long endTime = System.currentTimeMillis();
		_logger.info("Import Type took " + (endTime - startTime) + " milliseconds for url " + url + " and typename " + typeId);
		return type;
	}

	private CompositeType extractInfoFromComponent(Component component) throws NumberFormatException, NeuroMLException, LEMSException, GeppettoVisitingException
	{

		CompositeType compositeType = typeFactory.createCompositeType();
		ModelInterpreterUtils.initialiseNodeFromComponent(compositeType, component);

		// Parameter types
		for(ParamValue pv : component.getParamValues())
		{
			if(component.hasAttribute(pv.getName()))
			{
				compositeType.getVariables().add(ModelInterpreterUtils.createParameterTypeVariable(pv.getName(), component.getStringValue(pv.getName()), this.access));
			}
		}

		// Text types
		for(Entry<String, String> entry : component.getTextParamMap().entrySet())
		{
			compositeType.getVariables().add(ModelInterpreterUtils.createTextTypeVariable(entry.getKey(), entry.getValue(), this.access));
		}

		// Composite Type
		for(Entry<String, Component> entry : component.getRefComponents().entrySet())
		{
			if(!types.containsKey(entry.getValue().getID()))
			{
				types.put(entry.getValue().getID(), extractInfoFromComponent(entry.getValue()));
			}
			Variable variable = variablesFactory.createVariable();
			ModelInterpreterUtils.initialiseNodeFromComponent(variable, entry.getValue());
			variable.getTypes().add(types.get(entry.getValue().getID()));
			compositeType.getVariables().add(variable);
		}

		// Exposures are the variables that can potentially being watch.
		for(Exposure exposure : component.getComponentType().getExposures())
		{
			compositeType.getVariables().add(ModelInterpreterUtils.createExposureTypeVariable(exposure.getName(), Utils.getSIUnitInNeuroML(exposure.getDimension()).getSymbol(), this.access));
		}

		// Extracting populations
		for(Component population : component.getChildrenAL("populations"))
		{
			if(!types.containsKey(population.getID()))
			{
				createPopulationTypeVariable(population);
			}

			Variable variable = variablesFactory.createVariable();
			ModelInterpreterUtils.initialiseNodeFromComponent(variable, population);
			// variable.getInitialValues().put(key, value);
			variable.getTypes().add(types.get(population.getID()));
			compositeType.getVariables().add(variable);

		}

		for(Component projection : component.getChildrenAL("projections"))
		{
			createConnectionTypeVariablesForProjection(projection, compositeType);
		}

		for(Component componentChild : component.getAllChildren())
		{
			if(!componentChild.getDeclaredType().equals("population") && !componentChild.getDeclaredType().equals("projections"))
			{
				if(componentChild.getDeclaredType().equals("morphology"))
				{
					if(!types.containsKey(componentChild.getID()))
					{
						// We assume when we find a morphology it belongs to a cell
						ExtractVisualType extractVisualType = new ExtractVisualType(component, access);
						types.put(componentChild.getID(), extractVisualType.createTypeFromCellMorphology());
					}

					compositeType.setVisualType((VisualType) types.get(componentChild.getID()));
				}
				else
				{

					CompositeType anonymousCompositeType = extractInfoFromComponent(componentChild);
					if(anonymousCompositeType != null)
					{
						Variable variable = variablesFactory.createVariable();
						ModelInterpreterUtils.initialiseNodeFromComponent(variable, componentChild);
						variable.getAnonymousTypes().add(anonymousCompositeType);
						compositeType.getVariables().add(variable);
					}
				}
			}
		}

		return compositeType;

	}

	public void createConnectionTypeVariablesForProjection(Component projection, CompositeType compositeType) throws GeppettoVisitingException, LEMSException, NeuroMLException
	{
		if(!types.containsKey(projection.getRefComponents().get("synapse").getID()))
		{
			types.put(projection.getRefComponents().get("synapse").getID(), extractInfoFromComponent(projection.getRefComponents().get("synapse")));
		}

		// Pre and post synaptic population should be ref component but there are just attributes
		ArrayType prePopulationType = (ArrayType) types.get(projection.getAttributeValue("presynapticPopulation"));
		Variable prePopulationVariable = null;
		for(Variable variable : compositeType.getVariables())
		{
			if(variable.getId().equals(prePopulationType.getId()))
			{
				prePopulationVariable = variable;
				break;
			}
		}

		ArrayType postPopulationType = (ArrayType) types.get(projection.getAttributeValue("postsynapticPopulation"));
		Variable postPopulationVariable = null;
		for(Variable variable : compositeType.getVariables())
		{
			if(variable.getId().equals(postPopulationType.getId()))
			{
				postPopulationVariable = variable;
				break;
			}
		}

		for(Component projectionChild : projection.getAllChildren())
		{
			if(projectionChild.getDeclaredType().equals("connection"))
			{
				ConnectionType connectionType = typeFactory.createConnectionType();
				connectionType.setName(Resources.PROJECTION_ID + " - " + projection.getID() + " / " + Resources.CONNECTION + " - " + projectionChild.getID());
				connectionType.setId(Resources.CONNECTION.getId() + projection.getID() + projectionChild.getID());
				DomainModel domainModel = GeppettoFactory.eINSTANCE.createDomainModel();
				domainModel.setDomainModel(projectionChild);
				domainModel.setFormat(ServicesRegistry.getModelFormat("LEMS"));
				connectionType.setDomainModel(domainModel);

				Variable synapseVariable = variablesFactory.createVariable();
				ModelInterpreterUtils.initialiseNodeFromComponent(synapseVariable, projection.getRefComponents().get("synapse"));
				synapseVariable.getTypes().add(types.get(projection.getRefComponents().get("synapse").getID()));
				connectionType.getVariables().add(synapseVariable);

				Connection connection = valuesFactory.createConnection();
				connection.setConnectivity(Connectivity.DIRECTIONAL);

				for(Attribute attribute : projectionChild.getAttributes())
				{
					if(attribute.getName().equals("preCellId"))
					{
						String preCellId = ModelInterpreterUtils.parseCellRefStringForCellNum(attribute.getValue());
						Pointer prePointer = PointerUtility.getPointer(prePopulationVariable, prePopulationType, Integer.parseInt(preCellId));
						connection.getA().add(prePointer);
					}
					else if(attribute.getName().equals("postCellId"))
					{
						String postCellId = ModelInterpreterUtils.parseCellRefStringForCellNum(attribute.getValue());
						Pointer postPointer = PointerUtility.getPointer(postPopulationVariable, postPopulationType, Integer.parseInt(postCellId));
						connection.getB().add(postPointer);
					}
					else
					{
						// preSegmentId, preFractionAlong, postSegmentId, postFractionAlong
						connectionType.getVariables().add(ModelInterpreterUtils.createTextTypeVariable(attribute.getName(), attribute.getValue(), access));
					}
				}

				Variable variable = variablesFactory.createVariable();
				variable.setName(Resources.PROJECTION_ID + " - " + projection.getID() + " / " + Resources.CONNECTION + " - " + projectionChild.getID());
				variable.setId(Resources.CONNECTION.getId() + projection.getID() + projectionChild.getID());
				// variable.getInitialValues().put(connectionType, connection);
				variable.getAnonymousTypes().add(connectionType);
				compositeType.getVariables().add(variable);
			}
		}
	}

	public void createPopulationTypeVariable(Component populationComponent) throws GeppettoVisitingException, LEMSException, NeuroMLException
	{

		if(!types.containsKey(populationComponent.getRefComponents().get("component").getID()))
		{
			types.put(populationComponent.getRefComponents().get("component").getID(), extractInfoFromComponent(populationComponent.getRefComponents().get("component")));
		}
		CompositeType refCompositeType = (CompositeType) types.get(populationComponent.getRefComponents().get("component").getID());
		
		ArrayType arrayType = typeFactory.createArrayType();
		ModelInterpreterUtils.initialiseNodeFromComponent(arrayType, populationComponent);
		arrayType.setSize(Integer.parseInt(populationComponent.getStringValue("size")));

		if(!populationComponent.getRefComponents().get("component").getDeclaredType().equals("cell"))
		{
			if(!types.containsKey("morphology" + populationComponent.getID()))
			{
				VisualType visualType = typeFactory.createVisualType();
				// AQP Have a look at the id
				ModelInterpreterUtils.initialiseNodeFromString(visualType, "morphology" + populationComponent.getID());
				CompositeVisualType visualCompositeType = typeFactory.createCompositeVisualType();

				Sphere sphere = valuesFactory.createSphere();
				// SphereNode sphereNode = new SphereNode(id);
				sphere.setRadius(1.2d);

				Variable variable = variablesFactory.createVariable();
				variable.setId(populationComponent.getRefComponents().get("component").getID());
				variable.setName(populationComponent.getRefComponents().get("component").getID());
				variable.getTypes().add(this.access.getType(TypesPackage.Literals.VISUAL_TYPE));
				variable.getInitialValues().put(this.access.getType(TypesPackage.Literals.VISUAL_TYPE), sphere);

				visualCompositeType.getVariables().add(variable);

				types.put("morphology" + populationComponent.getID(), visualCompositeType);
			}

			refCompositeType.setVisualType((VisualType) types.get("morphology" + populationComponent.getID()));

		}
		arrayType.setArrayType(refCompositeType);

		ArrayValue arrayValue = valuesFactory.createArrayValue();

		String populationType = populationComponent.getTypeName();
		if(populationType != null && populationType.equals("populationList"))
		{

			for(Component populationChild : populationComponent.getAllChildren())
			{
				if(populationChild.getDeclaredType().equals("instance"))
				{
					Point point = null;
					for(Component instanceChild : populationChild.getAllChildren())
					{
						if(instanceChild.getDeclaredType().equals("location"))
						{
							point = valuesFactory.createPoint();
							point.setX(Double.parseDouble(instanceChild.getStringValue("x")));
							point.setY(Double.parseDouble(instanceChild.getStringValue("y")));
							point.setZ(Double.parseDouble(instanceChild.getStringValue("z")));
						}
					}

					ArrayElement arrayElement = valuesFactory.createArrayElement();
					arrayElement.setIndex(Integer.parseInt(populationChild.getID()));
					arrayElement.setPosition(point);
					arrayValue.getElements().add(arrayElement);

				}
			}

		}
		else
		{

		}
		arrayType.setDefaultValue(arrayValue);
		types.put(populationComponent.getID(), arrayType);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.geppetto.core.model.IModelInterpreter#getName()
	 */
	@Override
	public String getName()
	{
		return this.neuroMLModelInterpreterConfig.getModelInterpreterName();
	}

	@Override
	public void registerGeppettoService()
	{
		List<ModelFormat> modelFormats = new ArrayList<ModelFormat>(Arrays.asList(ServicesRegistry.registerModelFormat("NEUROML"), ServicesRegistry.registerModelFormat("LEMS")));
		ServicesRegistry.registerModelInterpreterService(this, modelFormats);
	}

	@Override
	public File downloadModel(Pointer pointer, ModelFormat format, IAspectConfiguration aspectConfiguration) throws ModelInterpreterException
	{
		DomainModel domainModel = PointerUtility.getType(pointer).getDomainModel();

		if(format.equals(ServicesRegistry.getModelFormat("LEMS")) || format.equals(ServicesRegistry.getModelFormat("NEUROML")))
		{
			try
			{
				// Create file and folder
				File outputFolder = PathConfiguration.createFolderInProjectTmpFolder(getScope(), projectId,
						PathConfiguration.getName(format.getModelFormat() + PathConfiguration.downloadModelFolderName, true));

				String outputFile = PointerUtility.getType(pointer).getId();

				// Serialise objects
				String serialisedModel = "";
				if(format.equals(ServicesRegistry.getModelFormat("LEMS")))
				{
					// Serialise LEMS object
					serialisedModel = XMLSerializer.serialize((Component) domainModel.getDomainModel());
					outputFile += "xml";
				}
				else
				{

					// LinkedHashMap<String, Standalone> neuroMLComponent = Utils.convertLemsComponentToNeuroML((Component)domainModel.getDomainModel());
					// NeuroMLDocument neuroMLDoc = new NeuroMLDocument();

					// Serialise NEUROML object
					// NeuroMLDocument neuroMLDoc = (NeuroMLDocument) ((ModelWrapper) model).getModel(ServicesRegistry.getModelFormat("NEUROML"));
					// NeuroMLConverter neuroMLConverter = new NeuroMLConverter();
					// serialisedModel = neuroMLConverter.neuroml2ToXml(neuroMLDoc);
					// // Change extension to nml
					// outputFile += "nml";
				}

				// Write to disc
				PrintWriter writer = new PrintWriter(outputFolder + outputFile);
				writer.print(serialisedModel);
				writer.close();
				return outputFolder;

			}
			catch(ContentError | IOException e)
			{
				throw new ModelInterpreterException(e);
			}

		}
		else
		{
			// Call conversion service
			LEMSConversionService lemsConversionService = new LEMSConversionService();
			lemsConversionService.setProjectId(projectId);
			lemsConversionService.setScope(Scope.CONNECTION);
			ExternalDomainModel outputDomainModel = null;
			try
			{
				outputDomainModel = (ExternalDomainModel) lemsConversionService.convert(domainModel, format, aspectConfiguration);
			}
			catch(ConversionException e)
			{
				throw new ModelInterpreterException(e);
			}
			return (File) outputDomainModel.getDomainModel();
		}

	}

	@Override
	public List<ModelFormat> getSupportedOutputs(Pointer pointer) throws ModelInterpreterException
	{
		List<ModelFormat> supportedOutputs = super.getSupportedOutputs(pointer);
		supportedOutputs.add(ServicesRegistry.getModelFormat("LEMS"));
		try
		{
			LEMSConversionService lemsConversionService = new LEMSConversionService();
			supportedOutputs.addAll(lemsConversionService.getSupportedOutputs(PointerUtility.getType(pointer).getDomainModel()));
		}
		catch(ConversionException e)
		{
			throw new ModelInterpreterException(e);
		}
		return supportedOutputs;
	}

}
