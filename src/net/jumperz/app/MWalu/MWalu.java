package net.jumperz.app.MWalu;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import net.jumperz.app.MWalu.util.MLargeFileReader;
import net.jumperz.app.MWalu.util.MUtil;
import net.jumperz.ds.iforest.MIFModel;
import net.jumperz.ds.iforest.MIFModelBuilder;
import net.jumperz.ds.iforest.MIFPOJOWriter;

public class MWalu
{
private static final int treeNumber = 1000;
private static final int subSampleSize = 10000;
protected static final char[] countedCharArray = new char[] { ':', '(', ';', '%', '/', '\'', '<', '?', '.', '#', };
protected static final String[] countedTwoCharArray = new String[] { "/%", "//", "/.", "..", "=/", "./", "/?" };

private static final java.text.DateFormat df = new java.text.SimpleDateFormat( "yyyy-MM-dd HH:mm:ss.SSS z" );
private final Map< String, AtomicInteger > ipSet = new ConcurrentHashMap<>( 10000 );
private final Map< String, Object[] > resultMap = new ConcurrentHashMap<>( 300000 );
private final List< double[] > vectorList = new ArrayList<>( 10000 );
final long start1 = System.currentTimeMillis();
DecimalFormat df2 = new DecimalFormat( "0.000" );

public static final List< String > featureNameList = new ArrayList<>();

static
{
	featureNameList.add( "findex_%" );
	featureNameList.add( "findex_:" );
	featureNameList.add( "char_:" );
	featureNameList.add( "char_(" );
	featureNameList.add( "char_;" );
	featureNameList.add( "char_%" );
	featureNameList.add( "char_/" );
	featureNameList.add( "char_'" );
	featureNameList.add( "char_<" );
	featureNameList.add( "char_?" );
	featureNameList.add( "char_." );
	featureNameList.add( "char_#" );
	featureNameList.add( "char_encode_=" );
	featureNameList.add( "char_encode_/" );
	featureNameList.add( "char_encode_\\" );
	featureNameList.add( "char_encode_%" );
	featureNameList.add( "%20" );
	featureNameList.add( "POST" );
	featureNameList.add( "path_nonalnum_count" );
	featureNameList.add( "pvalue_nonalnum_avg" );
	featureNameList.add( "non_alnum_len" );
	featureNameList.add( "non_alnum_count" );
	featureNameList.add( "/%" );
	featureNameList.add( "//" );
	featureNameList.add( "/." );
	featureNameList.add( ".." );
	featureNameList.add( "=/" );
	featureNameList.add( "./" );
	featureNameList.add( "/?" );
}

public static final int featureSize = featureNameList.size();

public void execute( final String[] args ) throws Exception
{
	p( "Reading file " + args[ 0 ] + " ..." );
	List< String > lines = new MLargeFileReader( args[ 0 ] ).readLines();
	List< String > linesNotShuffled = new ArrayList<>( lines );
	Collections.shuffle( lines );
	p( "Total number of lines: " + lines.size() );

	p( "Getting unique IP addresses ..." );
	lines.parallelStream().map( x -> initIpSet( x ) ).toArray();
	p( "Total number of IPs: " + ipSet.size() );

	p( "Constructing vector list ..." );
	lines.parallelStream().map( x -> getVector( x ) ).toArray();
	p( "Total number of vectors: " + vectorList.size() );

	p( "Building model ... ( treeNumber=" + treeNumber + " ,subSampleSize=" + subSampleSize + " )" );
	final MIFModelBuilder ifBuilder = new MIFModelBuilder( treeNumber );
	ifBuilder.setSubSampleSize( subSampleSize );
	ifBuilder.build( vectorList );

	p( "Writing model as Java source code ..." );
	final MIFPOJOWriter writer = new MIFPOJOWriter();
	final String code = writer.getCodeForLargeTree( "anomaly", "model", ifBuilder.getTreeList(), ifBuilder.getSubSampleSize() );
	try( OutputStream out = new FileOutputStream( "src/anomaly/model.java" ))
	{
		out.write( code.getBytes() );
	}

	p( "Compiling model with ant ..." );

	final Process process = Runtime.getRuntime().exec( new String[] { "/bin/bash", "-c", "ant" } );
	while( process.isAlive() )
	{
		Thread.sleep( 100 );
	}

	final MIFModel model = ( MIFModel )Class.forName( "anomaly.model" ).newInstance();
	p( "Model is successfully loaded." );

	p( "Scoring vectors ..." );
	final List< String > result2 = linesNotShuffled.parallelStream().map( x -> score( model, x ) ).collect( Collectors.toList() );
	final List< String > result1 = new ArrayList<>( resultMap.size() );
	for( Map.Entry< String, Object[] > entry : resultMap.entrySet() )
	{
		final Object[] value = entry.getValue();
		result1.add( value[ 0 ] + " " + value[ 1 ] );
	}

	p( "Sorting results ..." );
	final List< String > sortedList = result1.parallelStream().sorted().collect( Collectors.toList() );

	p( "Saving results ..." );
	Files.write( Paths.get( "data/result_by_ip.txt" ), sortedList );
	Files.write( Paths.get( "data/result_all.txt" ), result2 );
}

public static void main( String[] args ) throws Exception
{
	MWalu walu = new MWalu();
	walu.execute( args );

	p( "Total execution time:" + (System.currentTimeMillis() - walu.start1) );
}

public List< String > uniqueByIp( final List< String > list )
{
	Set< String > set = new HashSet<>( 10000 );
	final List< String > uniqueList = new ArrayList<>( 10000 );
	for( String s : list )
	{
		String s2 = s.split( "\t" )[ 1 ];
		String ip = s2.substring( 0, s2.indexOf( ' ' ) );
		if( set.add( ip ) )
		{
			uniqueList.add( s );
		}
	}
	return uniqueList;
}

public Object[] score( final String ip, final Object[] oldValue, final Double score, final String line )
{
	if( oldValue == null )
	{
		return new Object[] { score, line };
	}
	else
	{
		final Double oldScore = ( Double )oldValue[ 0 ];
		if( score < oldScore )
		{
			oldValue[ 0 ] = score;
			oldValue[ 1 ] = line;
		}
		return oldValue;
	}
}

public String score( final MIFModel model, final String s )
{
	try
	{
		final String[] array = s.split( "\"" );
		final double[] vector = toVector( array[ 1 ] );
		final String ip = array[ 0 ].split( " " )[ 0 ];
		final double score = model.getScore( vector );

		resultMap.compute( ip, ( k, v ) -> score( k, v, score, s ) );
		return df2.format( score ) + " " + s;
	}
	catch( Exception e )
	{
		//e.printStackTrace();
		return e.toString();
	}
}

public double[] toVector( final String requestLine ) throws MalformedURLException
{
	final String[] array = requestLine.split( " " );
	if( array.length != 3 )
	{
		return null;
	}
	final double[] vector = new double[ featureSize ];
	Arrays.fill( vector, -1 );
	final int[] charCountArray = MUtil.getCharCount( requestLine );
	final URL url = new URL( "http://dummy/" + array[ 1 ] );
	final String path = url.getPath();
	String query = url.getQuery();
	if( query == null )
	{
		query = "";
	}

	//findex_%
	int k = 0;
	vector[ k ] = MUtil.indexOfChar( requestLine, '%' );
	++k;

	//findex_:
	vector[ k ] = MUtil.indexOfChar( requestLine, ':' );
	++k;

	for( final char c : countedCharArray )
	{
		vector[ k ] = charCountArray[ c ];
		++k;
	}

	//encoded =
	vector[ k ] = MUtil.countIgnoreCase2( requestLine, "%3d" );
	++k;

	//encoded /
	vector[ k ] = MUtil.countIgnoreCase2( requestLine, "%2f" );
	++k;

	//encoded \
	vector[ k ] = MUtil.countIgnoreCase2( requestLine, "%5c" );
	++k;

	//encoded %
	vector[ k ] = MUtil.count( requestLine, "%25" );
	++k;

	//%20
	vector[ k ] = MUtil.count( requestLine, "%20" );
	++k;

	//POST
	vector[ k ] = requestLine.startsWith( "POST" ) ? 1 : 0;
	++k;

	//path_nonalnum_count
	vector[ k ] = path.length() - MUtil.getAlphaNumCount( path );
	++k;

	//pvalue_nonalnum_avg
	vector[ k ] = query.length() - MUtil.getAlphaNumCount( query );
	++k;

	//non_alnum_len(max_len)
	vector[ k ] = MUtil.getMaxNonAlnumLength( requestLine );
	++k;

	//non_alnum_count
	vector[ k ] = MUtil.getNonAlnumCount( requestLine );
	++k;

	for( int i = 0; i < countedTwoCharArray.length; ++i )
	{
		final String pattern = countedTwoCharArray[ i ];
		vector[ k ] = MUtil.count( requestLine, pattern );
		++k;
	}

	return vector;
}

public String getVector( final String s )
{
	try
	{
		final String ip = s.substring( 0, s.indexOf( ' ' ) );
		final AtomicInteger count = ipSet.get( ip );
		if( count.get() < 80 )
		{
			count.incrementAndGet();
			final String[] array = s.split( "\"" );
			final double[] vector = toVector( array[ 1 ] );
			if( vector != null )
			{
				synchronized( vectorList )
				{
					vectorList.add( vector );
				}
			}
		}
	}
	catch( Exception e )
	{
		e.printStackTrace();
	}
	return null;
}

public String initIpSet( final String s )
{
	final String ip = s.substring( 0, s.indexOf( ' ' ) );
	ipSet.computeIfAbsent( ip, x -> new AtomicInteger( 0 ) );
	return null;
}

public static void p( Object o )
{
	System.out.println( df.format( new Date() ) + ": " + o );
}
}