/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2014 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.message.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hibernate.proxy.HibernateProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.exception.IExceptionMessage;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.MetaAttachment;
import com.axelor.meta.db.MetaFile;
import com.axelor.tool.template.TemplateMaker;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

public class TemplateMessageServiceImpl implements TemplateMessageService {

	private static final String RECIPIENT_SEPARATOR = ";|,";
	private static final char TEMPLATE_DELIMITER = '$';
	
	private final Logger log = LoggerFactory.getLogger( getClass() );

	protected TemplateMaker maker;
	
	private MessageService messageService;
	
	private EmailAddressRepository emailAddressRepo;

	@Inject
	public TemplateMessageServiceImpl( MessageService messageService, EmailAddressRepository emailAddressRepo ){
		this.messageService = messageService;
		this.emailAddressRepo = emailAddressRepo;
	}

	@Override
	public Message generateMessage(Model model, Template template) throws ClassNotFoundException, InstantiationException, IllegalAccessException, AxelorException, IOException  {
		
		Class<?> klass = model.getClass();
		if ( model instanceof HibernateProxy ) { klass = ( (HibernateProxy) model ).getHibernateLazyInitializer().getPersistentClass(); }
		return generateMessage( model.getId(), klass.getCanonicalName(), klass.getSimpleName(), template);
		
	}
	
	@Override
	public Message generateMessage( long objectId, String model, String tag, Template template ) throws ClassNotFoundException, InstantiationException, IllegalAccessException, AxelorException, IOException  {
		
		if ( !model.equals( template.getMetaModel().getFullName() ) ){
			throw new AxelorException( I18n.get(IExceptionMessage.TEMPLATE_SERVICE_3 ), IException.INCONSISTENCY, template.getMetaModel().getFullName() );
		}
		
		log.debug("model : {}", model);
		log.debug("tag : {}", tag);
		log.debug("object id : {}", objectId);
		log.debug("template : {}", template);
		
		initMaker(objectId, model, tag);
		
		String content = "", subject = "", from= "", replyToRecipients = "", toRecipients = "", ccRecipients = "", bccRecipients = "", addressBlock= "";
		int mediaTypeSelect;
		
		if ( !Strings.isNullOrEmpty( template.getContent() ) )  {
			//Set template
			maker.setTemplate(template.getContent());
			content = maker.make();
		}
		
		if( !Strings.isNullOrEmpty( template.getAddressBlock() ) )  {
			maker.setTemplate(template.getAddressBlock());
			//Make it
			addressBlock = maker.make();
		}
		
		if ( !Strings.isNullOrEmpty( template.getSubject() ) )  {
			maker.setTemplate(template.getSubject());
			subject = maker.make();
			log.debug( "Subject :::", subject );
		}
		
		if( !Strings.isNullOrEmpty( template.getFromAdress() ) )  {
			maker.setTemplate(template.getFromAdress());
			from = maker.make();
			log.debug( "From :::", from );
		}
		
		if( !Strings.isNullOrEmpty( template.getReplyToRecipients() ) )  {
			maker.setTemplate(template.getReplyToRecipients());
			replyToRecipients = maker.make();
			log.debug( "Reply to :::", replyToRecipients );
		}
		
		if(template.getToRecipients() != null)  {
			maker.setTemplate(template.getToRecipients());
			toRecipients = maker.make();
			log.debug( "To :::", toRecipients );
		}
		
		if(template.getCcRecipients() != null)  {
			maker.setTemplate(template.getCcRecipients());
			ccRecipients = maker.make();
			log.debug( "CC :::", ccRecipients );
		}
		
		if(template.getBccRecipients() != null)  {
			maker.setTemplate(template.getBccRecipients());
			bccRecipients = maker.make();
			log.debug( "BCC :::", bccRecipients );
		}
		
		mediaTypeSelect = template.getMediaTypeSelect();
		log.debug( "Media :::", mediaTypeSelect );
		log.debug( "Content :::", content );
		
		return messageService.createMessage( model, Long.valueOf(objectId).intValue(), subject,  content, getEmailAddress(from), getEmailAddresses(replyToRecipients),
				getEmailAddresses(toRecipients), getEmailAddresses(ccRecipients), getEmailAddresses(bccRecipients),
				getMetaFiles(template), addressBlock, mediaTypeSelect );		
	}
	
	public Set<MetaFile> getMetaFiles( Template template ) throws AxelorException, IOException {
		
		List<MetaAttachment> metaAttachments = Query.of( MetaAttachment.class ).filter( "self.objectId = ?1 AND self.objectName = ?2", template.getId(), Template.class.getName() ).fetch();
		Set<MetaFile> metaFiles = Sets.newHashSet();
		for ( MetaAttachment metaAttachment: metaAttachments ){ metaFiles.add( metaAttachment.getMetaFile() ); }
		
		log.debug("Metafile to attach: {}", metaFiles);
		return metaFiles;

	}
	
	
	@SuppressWarnings("unchecked")
	public TemplateMaker initMaker( long objectId, String model, String tag ) throws InstantiationException, IllegalAccessException, ClassNotFoundException  {
		//Init the maker
		this.maker = new TemplateMaker( Locale.FRENCH, TEMPLATE_DELIMITER, TEMPLATE_DELIMITER);
		
		Class<? extends Model> myClass = (Class<? extends Model>) Class.forName( model );
		maker.setContext( JPA.find( myClass, objectId), tag );
		
		return maker;
		
	}
	
	protected List<EmailAddress> getEmailAddresses( String recipients ) {

		List<EmailAddress> emailAddressList = Lists.newArrayList();
		if ( Strings.isNullOrEmpty( recipients ) )  { return emailAddressList; }	
		
		for ( String recipient : recipients.split(RECIPIENT_SEPARATOR) )  { emailAddressList.add( getEmailAddress( recipient ) ); }
		return emailAddressList;
	}
	
	
	protected EmailAddress getEmailAddress( String recipient )  {
		
		if ( Strings.isNullOrEmpty(recipient) ) { return null; }
		
		EmailAddress emailAddress = emailAddressRepo.findByAddress(recipient);
		
		if ( emailAddress == null )  {
			Map<String, Object> values = new HashMap<String,Object>();
			values.put("address", recipient);
			emailAddress = emailAddressRepo.create(values);
		}
		
		return emailAddress;
	}
}
