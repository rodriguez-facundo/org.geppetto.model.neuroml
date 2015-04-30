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
package org.geppetto.model.neuroml.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import junit.framework.Assert;

import org.geppetto.core.conversion.ConversionException;
import org.geppetto.core.model.ModelInterpreterException;
import org.geppetto.core.model.ModelWrapper;
import org.geppetto.core.services.IModelFormat;
import org.geppetto.model.neuroml.services.LEMSConversionService;
import org.geppetto.model.neuroml.services.LEMSModelInterpreterService;
import org.geppetto.model.neuroml.services.ModelFormat;
import org.junit.AfterClass;
import org.junit.Test;
import org.lemsml.jlems.api.LEMSBuildException;
import org.lemsml.jlems.core.sim.LEMSException;

/**
 * @author Adrian Quintana (adrian.perez@ucl.ac.uk)
 * 
 */
public class LEMSConversionServiceTest
{

	/**
	 * "" Test method for {@link org.geppetto.model.neuroml.services.LEMSConversionService#readModel(java.net.URL)}.
	 * 
	 * @throws ModelInterpreterException
	 * @throws IOException
	 * @throws LEMSException
	 * @throws LEMSBuildException
	 */
	@Test
	public void testNeuron() throws ConversionException, ModelInterpreterException, LEMSException, IOException
	{
		LEMSConversionService lemsConversionService = new LEMSConversionService();

		// HH
		LEMSModelInterpreterService modelInterpreter = new LEMSModelInterpreterService();
		URL url = new URL("https://raw.githubusercontent.com/openworm/org.geppetto.samples/development/LEMS/SingleComponentHH/LEMS_NML2_Ex5_DetCell.xml");

		ModelWrapper modelWrapper = (ModelWrapper) modelInterpreter.readModel(url, null, "");
		ModelWrapper outputModel = (ModelWrapper) lemsConversionService.convert(modelWrapper, ModelFormat.LEMS, ModelFormat.NEURON);
		
		String outputFileName = (String) outputModel.getModel(ModelFormat.NEURON);
		File file = new File(outputFileName);
		assertTrue(file.exists());

	}
	
	/**
	 * "" Test method for {@link org.geppetto.model.neuroml.services.LEMSConversionService#readModel(java.net.URL)}.
	 * 
	 * @throws ModelInterpreterException
	 * @throws IOException
	 * @throws LEMSException
	 * @throws LEMSBuildException
	 */
	@Test
	public void testAllAvailableModelsForHH() throws ConversionException, ModelInterpreterException, LEMSException, IOException
	{
		LEMSConversionService lemsConversionService = new LEMSConversionService();

		// HH
		LEMSModelInterpreterService modelInterpreter = new LEMSModelInterpreterService();
		URL url = new URL("https://raw.githubusercontent.com/openworm/org.geppetto.samples/development/LEMS/SingleComponentHH/LEMS_NML2_Ex5_DetCell.xml");

		ModelWrapper modelWrapper = (ModelWrapper) modelInterpreter.readModel(url, null, "");
		for (IModelFormat modelFormat : lemsConversionService.getSupportedOutputs(modelWrapper, ModelFormat.LEMS)){
			System.out.println(modelFormat);
			ModelWrapper outputModel = (ModelWrapper) lemsConversionService.convert(modelWrapper, ModelFormat.LEMS, modelFormat);
			
			String outputFileName = (String) outputModel.getModel(modelFormat);
			File file = new File(outputFileName);
			assertTrue(file.exists());
		}

	}

	/**
	 * "" Test method for {@link org.geppetto.model.neuroml.services.LEMSConversionService#readModel(java.net.URL)}.
	 * 
	 * @throws ModelInterpreterException
	 * @throws IOException
	 * @throws LEMSException
	 * @throws LEMSBuildException
	 */
	@Test
	public void testOutputsFormats() throws ConversionException, ModelInterpreterException, LEMSException, IOException
	{
		LEMSConversionService lemsConversionService = new LEMSConversionService();

		// HH
		LEMSModelInterpreterService modelInterpreter = new LEMSModelInterpreterService();
		URL url = new URL("https://raw.githubusercontent.com/openworm/org.geppetto.samples/development/LEMS/SingleComponentHH/LEMS_NML2_Ex5_DetCell.xml");

		ModelWrapper modelWrapper = (ModelWrapper) modelInterpreter.readModel(url, null, "");

		List<IModelFormat> modelFormats = lemsConversionService.getSupportedOutputs();
		Assert.assertTrue(modelFormats.size() > 0 );
		
		modelFormats = lemsConversionService.getSupportedOutputs(modelWrapper, ModelFormat.LEMS);
		Assert.assertTrue(modelFormats.size() > 0 );
	}

	@AfterClass
    public static void teardown() throws Exception {
		File tmp = new File(System.getProperty("user.dir")+"/geppettoTmp");
		if(tmp.exists()){
			deleteDirectory(tmp);
		}
    }
	
	public static boolean deleteDirectory(File directory) {
	    if(directory.exists()){
	        File[] files = directory.listFiles();
	        if(null!=files){
	            for(int i=0; i<files.length; i++) {
	                if(files[i].isDirectory()) {
	                    deleteDirectory(files[i]);
	                }
	                else {
	                    files[i].delete();
	                }
	            }
	        }
	    }
	    return(directory.delete());
	}

}