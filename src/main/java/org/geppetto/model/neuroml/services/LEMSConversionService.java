/*******************************************************************************
 * The MIT License (MIT)
 *
 * Copyright (c) 2011, 2013 OpenWorm.
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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geppetto.core.beans.PathConfiguration;
import org.geppetto.core.conversion.AConversion;
import org.geppetto.core.conversion.ConversionException;
import org.geppetto.core.data.model.IAspectConfiguration;
import org.geppetto.core.data.model.IInstancePath;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.services.registry.ServicesRegistry;
import org.geppetto.model.DomainModel;
import org.geppetto.model.ExternalDomainModel;
import org.geppetto.model.GeppettoFactory;
import org.geppetto.model.ModelFormat;
import org.geppetto.model.neuroml.modelinterpreter.utils.ModelInterpreterUtils;
import org.geppetto.model.neuroml.utils.ModelFormatMapping;
import org.geppetto.model.util.GeppettoModelException;
import org.geppetto.model.values.Pointer;
import org.geppetto.model.values.PointerElement;
import org.lemsml.export.base.IBaseWriter;
import org.lemsml.jlems.core.sim.ContentError;
import org.lemsml.jlems.core.sim.LEMSException;
import org.lemsml.jlems.core.type.Component;
import org.lemsml.jlems.core.type.ComponentType;
import org.lemsml.jlems.core.type.Lems;
import org.lemsml.jlems.core.xml.XMLAttribute;
import org.neuroml.export.utils.ExportFactory;
import org.neuroml.export.utils.Format;
import org.neuroml.export.utils.SupportedFormats;
import org.neuroml.export.utils.Utils;
import org.neuroml.model.util.NeuroMLConverter;
import org.neuroml.model.util.NeuroMLException;
import org.springframework.stereotype.Service;

/**
 * @author Adrian Quintana (adrian.perez@ucl.ac.uk)
 * 
 */
@Service
public class LEMSConversionService extends AConversion
{

	private static Log _logger = LogFactory.getLog(LEMSConversionService.class);

	@Override
	public List<ModelFormat> getSupportedInputs() throws ConversionException
	{
		return new ArrayList<ModelFormat>(Arrays.asList(ServicesRegistry.getModelFormat("LEMS")));
	}

	@Override
	public void registerGeppettoService() throws ConversionException
	{
		// Input Model Format
		List<ModelFormat> inputModelFormats = new ArrayList<ModelFormat>(Arrays.asList(ServicesRegistry.registerModelFormat("LEMS")));

		// Output Model Formats
		List<ModelFormat> outputModelFormats = new ArrayList<ModelFormat>();
		for(Format format : SupportedFormats.getSupportedOutputs())
		{
			// Convert from export formats to Geppetto formats
			ModelFormatMapping modelFormatMapping = ModelFormatMapping.fromExportValue(format.toString());
			if(modelFormatMapping != null)
			{
				ModelFormat modelFormat = ServicesRegistry.registerModelFormat(modelFormatMapping.name());
				if(modelFormat != null) outputModelFormats.add(modelFormat);
			}
		}

		ServicesRegistry.registerConversionService(this, inputModelFormats, outputModelFormats);
	}

	@Override
	public List<ModelFormat> getSupportedOutputs() throws ConversionException
	{
		_logger.info("Getting supported outputs");
		List<ModelFormat> modelFormats = new ArrayList<ModelFormat>();

		for(Format format : SupportedFormats.getSupportedOutputs())
		{
			// Convert from export formats to Geppetto formats
			ModelFormatMapping modelFormatMapping = ModelFormatMapping.fromExportValue(format.toString());
			if(modelFormatMapping != null)
			{
				ModelFormat modelFormat = ServicesRegistry.getModelFormat(modelFormatMapping.name());
				if(modelFormat != null) modelFormats.add(modelFormat);
			}
		}
		return modelFormats;
	}

	@Override
	public List<ModelFormat> getSupportedOutputs(DomainModel model) throws ConversionException
	{
		_logger.info("Getting supported outputs for a specific model and input format " + model.getFormat());
		List<ModelFormat> modelFormats = new ArrayList<ModelFormat>();

		try
		{
			// Read LEMS component to convert and add to the LEMS file
			Lems lems = new Lems();
			lems.addComponent((Component) model.getDomainModel());
			ModelInterpreterUtils.processLems(lems);

			// Get supported outputs and add them to the model formats list
			for(Format format : SupportedFormats.getSupportedOutputs(lems))
			{
				// Convert from export formats to Geppetto formats
				ModelFormat modelFormat = ServicesRegistry.getModelFormat(ModelFormatMapping.fromExportValue(format.toString()).name());
				if(modelFormat != null) modelFormats.add(modelFormat);
			}
		}
		catch(NeuroMLException | LEMSException e)
		{
			e.printStackTrace();
			_logger.error("NeuroMLException or LEMS exception caught while getting supported outputs");
			throw new ConversionException(e);
		}
		return modelFormats;
	}

	@Override
	public DomainModel convert(DomainModel model, ModelFormat output, IAspectConfiguration aspectConfig, GeppettoModelAccess modelAccess) throws ConversionException
	{
		_logger.info("Converting model from " + model.getFormat() + " to " + output.getModelFormat());
		// AQP: Review if this was commented out
		// checkSupportedFormat(input);

		ExternalDomainModel outputModel = GeppettoFactory.eINSTANCE.createExternalDomainModel();
		try
		{
			// Create LEMS file with NML dependencies
			// Lems lems = Utils.getLemsWithNML2CompTypes();

			// Create LEMS file with NML dependencies
			Lems lems = Utils.readLemsNeuroMLFile(NeuroMLConverter.convertNeuroML2ToLems("<neuroml></neuroml>")).getLems();

			// Read LEMS component to convert and add to the LEMS file
			Component component = (Component) model.getDomainModel();
			lems.addComponent(component);

			// Process LEMS
			ModelInterpreterUtils.processLems(lems);

			// Create Folder
			File outputFolder = PathConfiguration.createFolderInProjectTmpFolder(getScope(), projectId,
					PathConfiguration.getName(output.getModelFormat() + PathConfiguration.downloadModelFolderName, true));

			// FIXME: When we can convert models without targets this needs to be changed (currently the export library can only convert models with a target component)

			// Extracting watch variables from aspect configuration
			PrintWriter writer = new PrintWriter(outputFolder + "/outputMapping.dat");

			if(aspectConfig != null)
			{
				// FIXME: Units in seconds
				Component simulationComponent = new Component("sim1", new ComponentType("Simulation"));
				simulationComponent.addAttribute(new XMLAttribute("length", Float.toString(aspectConfig.getSimulatorConfiguration().getLength()) + "s"));
				simulationComponent.addAttribute(new XMLAttribute("step", Float.toString(aspectConfig.getSimulatorConfiguration().getTimestep()) + "s"));
				simulationComponent.addAttribute(new XMLAttribute("target", aspectConfig.getSimulatorConfiguration().getParameters().get("target")));

				// Create output file component and add file to outputmapping file
				Component outputFile = new Component("outputFile1", new ComponentType("OutputFile"));
				outputFile.addAttribute(new XMLAttribute("fileName", "results/results.dat"));
				writer.println("results/results.dat");

				// Add outputcolumn and variable to outputmapping file per watch variable
				String variables = "time(StateVariable)";
				if(aspectConfig.getWatchedVariables() != null)
				{
					for(IInstancePath watchedVariable : aspectConfig.getWatchedVariables())
					{
						String instancePath = watchedVariable.getInstancePath();

						// Create output column component
						Component outputColumn = new Component(instancePath.substring(instancePath.lastIndexOf(".") + 1).replace("(", "_").replace(")", ""), new ComponentType("OutputColumn"));

						// Convert from Geppetto to LEMS Path
						// outputColumn.addAttribute(new XMLAttribute("quantity", quantity));
						// outputColumn.addAttribute(new XMLAttribute("quantity", "baskets_12/" + i + "/bask/0/v"));
						outputColumn.addAttribute(new XMLAttribute("quantity", extractLEMSPath(component, modelAccess.getPointer(instancePath))));

						// Add output column component to file
						outputFile.addComponent(outputColumn);
						variables += " " + watchedVariable.getInstancePath();
					}
				}
				writer.println(variables);

				// Add block to lems and process lems doc
				simulationComponent.addComponent(outputFile);
				lems.addComponent(simulationComponent);
				lems.setTargetComponent(simulationComponent);
				ModelInterpreterUtils.processLems(lems);

			}

			writer.close();

			String outputFileName = "";
			if(convertModel)
			{
				// FIXME: the py extension can be added inside.
				outputFileName = "main_script.py";

				// Convert model
				IBaseWriter exportWriter = ExportFactory.getExportWriter(lems, outputFolder, outputFileName, ModelFormatMapping.valueOf(output.getModelFormat()).getExportValue());
				List<File> outputFiles = exportWriter.convert();
			}

			// Create model from converted model, if we are not converting we send the outputFolder
			outputModel.setDomainModel(outputFolder + File.separator + outputFileName);
			outputModel.setFormat(output);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new ConversionException(e);
		}

		return outputModel;
	}

	// Check whether main component is a network or a cell. If it is a network, return the type of population, otherwise return cell
	// Returned value will define the lems path format
	public static String getSimulationTreePathType(Component targetComponent)
	{
		if(targetComponent.getDeclaredType().equals("network"))
		{
			// It is a network
			for(Component componentChild : targetComponent.getAllChildren())
			{
				if(componentChild.getDeclaredType().equals("population"))
				{
					// population = componentChild;
					if(componentChild.getComponentType().getName().equals("populationList"))
					{
						return "populationList";
					}
				}
			}
			return "population";

		}
		else
		{
			// It is a cell
			return "cell";
		}

	}

	/**
	 * @param token
	 * @return
	 * @throws GeppettoModelException
	 */
	private String extractLEMSPath(Component component, Pointer watchedPointer) throws ContentError, GeppettoModelException
	{
		String lemsPath = "";

		// First we identify what sort of network/cell it is and depending on this we will generate the Simulation Tree format
		String simulationTreePathType = getSimulationTreePathType(component);
		// populationList,population,cell

		Iterator<PointerElement> elementIterator = watchedPointer.getElements().iterator();
		while(elementIterator.hasNext())
		{
			PointerElement pointerElement = elementIterator.next();

			String instancePath = (component.getID() != null) ? component.getID() : component.getDeclaredType();

			// String token = st.nextToken();
			if(!elementIterator.hasNext())
			{
				lemsPath += "/" + pointerElement.getVariable().getId();
			}
			else if(!instancePath.equals(pointerElement.getType().getId()))
			{

				for(Component componentChild : component.getAllChildren())
				{
					String componentChildInstancePath = (componentChild.getID() != null) ? componentChild.getID() : componentChild.getDeclaredType();
					if(componentChildInstancePath.equals(pointerElement.getType().getId()))
					{
						component = componentChild;
						instancePath = componentChildInstancePath;
						break;
					}
				}

				if(component.getDeclaredType().equals("population"))
				{

					String populationSize = component.getStringValue("size");

					component = component.getRefComponents().get("component");

					// Create path for cells and network
					if(Integer.parseInt(populationSize) == 1)
					{
						lemsPath += instancePath + "[0]";
					}
					else
					{
						if(simulationTreePathType.equals("populationList"))
						{
							// FIXME AQP What to do with the different segments?
							lemsPath += instancePath + "/" + pointerElement.getIndex() + "/" + component.getID() + "/0";
						}
						else
						{
							lemsPath += instancePath + "[" + pointerElement.getIndex() + "]";
						}
					}
				}
				else
				{
					lemsPath += "/" + pointerElement.getType().getId();
				}
			}
		}

		System.out.println("lemsPath");
		System.out.println(lemsPath);
		return lemsPath;
	}

}
