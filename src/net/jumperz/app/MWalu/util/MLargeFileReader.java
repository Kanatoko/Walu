package net.jumperz.app.MWalu.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public class MLargeFileReader
{
private AtomicLong len = new AtomicLong( 0 );
private final int threadCount = ForkJoinPool.getCommonPoolParallelism();

private String[] headArray = new String[ threadCount - 1 ];
private String[] tailArray = new String[ threadCount - 1 ];
private List< String >[] listArray = new List[ threadCount ];
private final String fileName;

private int read( final int index, final int threadCount )
{
	try( RandomAccessFile file = new RandomAccessFile( fileName, "r" ))
	{
		final long size = file.length();
		long eachSize = size / threadCount;
		final long offset = index * eachSize;
		final boolean lastBlock = (index == threadCount - 1);
		if( lastBlock )
		{
			//last block
			eachSize = size - (eachSize * index);
		}
		final byte[] buf = new byte[ ( int )eachSize ];
		file.seek( offset );
		file.readFully( buf );

		List< String > lines = new ArrayList<>( 300000 );
		listArray[ index ] = lines;
		int start = 0;
		for( int i = 0; i < buf.length; ++i )
		{
			if( buf[ i ] == ( byte )0x0A )
			{
				//head
				if( index > 0 && start == 0 )
				{
					if( i > 0 )
					{
						String head = new String( buf, start, i - start, StandardCharsets.UTF_8 );
						headArray[ index - 1 ] = head;
					}
				}
				else
				{
					lines.add( new String( buf, start, i - start, StandardCharsets.UTF_8 ) );
				}
				start = i + 1;
			}
		}

		if( !lastBlock )
		{
			//tail
			if( start == buf.length ) //last byte is 0x0A
			{
			}
			else
			{
				final String tail = new String( buf, start, buf.length - start, StandardCharsets.UTF_8 );
				tailArray[ index ] = tail;
			}
		}

		len.addAndGet( lines.size() );
	}
	catch( IOException e )
	{
		e.printStackTrace();
	}
	return index;
}

public MLargeFileReader(final String fileName)
{
	this.fileName = fileName;
	String blank = "";
	for( int i = 0; i < headArray.length; ++i )
	{
		headArray[ i ] = blank;
		tailArray[ i ] = blank;
	}
}

public List< String > readLines() throws IOException
{
	IntStream.range( 0, threadCount ).parallel().map( i -> read( i, threadCount ) ).toArray();

	List< String > lines = new ArrayList<>( ( int )len.get() );
	for( int i = 0; i < threadCount; ++i )
	{
		List< String > eachLines = listArray[ i ];
		if( i != threadCount - 1 )
		{
			eachLines.add( tailArray[ i ] + headArray[ i ] );
		}
		lines.addAll( eachLines );
	}

	for( List< String > l : listArray )
	{
		l.clear();
	}
	return lines;
}

}